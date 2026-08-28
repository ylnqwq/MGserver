# -*- coding: utf-8 -*-
"""Business-facing microgrid daily dispatch service based on MOIABC.

This module intentionally avoids the experiment/comparison pipeline in
``multi_objective.application_point``.  It accepts user profile files, builds a
daily microgrid dispatch model, runs only MOIABC, and exports chart-ready data.
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
import time
import zipfile
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any
from xml.etree import ElementTree

import numpy as np

ROOT_DIR = Path(__file__).resolve().parents[1]
if str(ROOT_DIR) not in sys.path:
    sys.path.insert(0, str(ROOT_DIR))

from multi_objective.algorithms import MOIABC
from multi_objective.application_point.microgrid_dispatch_model import (
    BASE_BUY_PRICE,
    BASE_LOAD_KW,
    BASE_PV_KW,
    BASE_SELL_PRICE,
    BASE_WT_KW,
    DIESEL_EMISSION_FACTOR,
    GRID_EMISSION_FACTOR,
    TREATMENT_COST,
    MicrogridParams,
)


DEFAULT_TIME_STEP_HOURS = 0.5
DEFAULT_PERIODS = int(24 / DEFAULT_TIME_STEP_HOURS)

COLUMN_ALIASES = {
    "time": {"time", "hour", "hours", "h", "时刻", "时间", "小时"},
    "load_kw": {"load_kw", "load", "demand", "负荷", "负荷kw", "用电负荷", "电负荷"},
    "pv_kw": {"pv_kw", "pv", "solar", "photovoltaic", "光伏", "光伏kw", "光伏出力"},
    "wt_kw": {"wt_kw", "wind_kw", "wind", "wind_turbine", "风电", "风机", "风电kw", "风电出力"},
    "buy_price": {"buy_price", "grid_buy_price", "price", "buy", "购电价", "购电电价", "电价", "买电价"},
    "sell_price": {"sell_price", "grid_sell_price", "sell", "售电价", "售电电价", "卖电价", "上网电价"},
}


@dataclass(frozen=True)
class MoiabcRunConfig:
    bee: int = 60
    max_iter: int = 300
    limit: int = 120
    archive_size: int = 80
    tournament_size: int = 3
    elite_rate: float = 0.25
    elimination_rate: float = 0.25
    archive_guidance_rate: float = 0.40
    seed: int | None = 20260723


@dataclass(frozen=True)
class DailyProfiles:
    load_kw: np.ndarray
    pv_kw: np.ndarray
    wt_kw: np.ndarray
    buy_price: np.ndarray
    sell_price: np.ndarray
    time_step_hours: float = DEFAULT_TIME_STEP_HOURS

    @property
    def periods(self) -> int:
        return int(self.load_kw.size)


def expand_hourly_profile(values: np.ndarray, time_step_hours: float = DEFAULT_TIME_STEP_HOURS) -> np.ndarray:
    repeat_count = int(round(1.0 / time_step_hours))
    return np.repeat(np.asarray(values, dtype=float), repeat_count)


def expand_hourly_profile_with_midpoints(values: np.ndarray) -> np.ndarray:
    values = np.asarray(values, dtype=float)
    expanded = np.empty(values.size * 2, dtype=float)
    expanded[0::2] = values
    expanded[1::2] = 0.5 * (values + np.roll(values, -1))
    return expanded


def default_daily_profiles() -> DailyProfiles:
    return DailyProfiles(
        load_kw=expand_hourly_profile_with_midpoints(BASE_LOAD_KW),
        pv_kw=expand_hourly_profile_with_midpoints(BASE_PV_KW),
        wt_kw=expand_hourly_profile_with_midpoints(BASE_WT_KW),
        buy_price=expand_hourly_profile(BASE_BUY_PRICE),
        sell_price=expand_hourly_profile(BASE_SELL_PRICE),
    )


class MicrogridDispatchModel:
    def __init__(self, profiles: DailyProfiles, params: MicrogridParams | None = None):
        self.profiles = profiles
        self.params = params or MicrogridParams(time_step_hours=profiles.time_step_hours)
        self.hours = profiles.periods
        self.bounds = (
            [(0.0, self.params.diesel_max_kw)] * self.hours
            + [(-self.params.battery_charge_max_kw, self.params.battery_discharge_max_kw)] * self.hours
        )
        if self.params.enable_curtailment:
            self.bounds = self.bounds + [(0.0, 1.0)] * self.hours + [(0.0, 1.0)] * self.hours

    def split_solution(self, solution: np.ndarray) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
        values = np.asarray(solution, dtype=float)
        if values.size == 2 * self.hours:
            zeros = np.zeros(self.hours, dtype=float)
            return values[: self.hours], values[self.hours :], zeros, zeros
        if values.size == 4 * self.hours:
            return (
                values[: self.hours],
                values[self.hours : 2 * self.hours],
                values[2 * self.hours : 3 * self.hours],
                values[3 * self.hours :],
            )
        raise ValueError(f"Expected {2 * self.hours} or {4 * self.hours} decision variables, got {values.size}.")

    def renewable_surplus_profile(self, wt_kw: np.ndarray | None = None, pv_kw: np.ndarray | None = None) -> np.ndarray:
        wt_kw = self.profiles.wt_kw if wt_kw is None else np.asarray(wt_kw, dtype=float)
        pv_kw = self.profiles.pv_kw if pv_kw is None else np.asarray(pv_kw, dtype=float)
        return np.maximum(wt_kw + pv_kw - self.profiles.load_kw, 0.0)

    def repair_curtailment(
        self,
        raw_wind_curtail_fraction: np.ndarray,
        raw_pv_curtail_fraction: np.ndarray,
    ) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
        if not self.params.enable_curtailment:
            zeros = np.zeros(self.hours, dtype=float)
            return zeros, zeros, self.profiles.wt_kw.copy(), self.profiles.pv_kw.copy()

        wind_curtail_fraction = np.clip(np.asarray(raw_wind_curtail_fraction, dtype=float), 0.0, 1.0)
        pv_curtail_fraction = np.clip(np.asarray(raw_pv_curtail_fraction, dtype=float), 0.0, 1.0)
        wind_curtail_kw = wind_curtail_fraction * self.profiles.wt_kw
        pv_curtail_kw = pv_curtail_fraction * self.profiles.pv_kw
        return wind_curtail_kw, pv_curtail_kw, self.profiles.wt_kw - wind_curtail_kw, self.profiles.pv_kw - pv_curtail_kw

    def repair_battery_power(self, raw_battery_kw: np.ndarray, wt_kw: np.ndarray, pv_kw: np.ndarray) -> np.ndarray:
        repaired = np.zeros(self.hours, dtype=float)
        energy = self.params.soc_initial * self.params.battery_capacity_kwh
        min_energy = self.params.soc_min * self.params.battery_capacity_kwh
        max_energy = self.params.soc_max * self.params.battery_capacity_kwh
        initial_energy = energy
        renewable_surplus_kw = self.renewable_surplus_profile(wt_kw, pv_kw)

        for hour in range(self.hours):
            remaining_hours = self.hours - hour - 1
            requested_power = raw_battery_kw[hour]
            max_discharge = min(
                self.params.battery_discharge_max_kw,
                max(0.0, (energy - min_energy) * self.params.discharge_efficiency / self.params.time_step_hours),
            )
            max_charge = min(
                self.params.battery_charge_max_kw,
                max(0.0, (max_energy - energy) / (self.params.charge_efficiency * self.params.time_step_hours)),
                renewable_surplus_kw[hour],
            )
            ramp_lower = -max_charge
            ramp_upper = max_discharge
            if self.params.enable_extra_complexity and hour > 0:
                ramp_lower = max(ramp_lower, repaired[hour - 1] - self.params.battery_ramp_limit_kw)
                ramp_upper = min(ramp_upper, repaired[hour - 1] + self.params.battery_ramp_limit_kw)
            power = float(np.clip(requested_power, -max_charge, max_discharge))
            power = float(np.clip(power, ramp_lower, ramp_upper))

            future_charge_capacity = float(
                np.sum(np.minimum(self.params.battery_charge_max_kw, renewable_surplus_kw[hour + 1 :]))
                * self.params.time_step_hours
                * self.params.charge_efficiency
            )
            min_reachable_energy = max(min_energy, initial_energy - future_charge_capacity)
            max_reachable_energy = min(
                max_energy,
                initial_energy
                + remaining_hours * self.params.battery_discharge_max_kw * self.params.time_step_hours
                / self.params.discharge_efficiency,
            )
            next_energy = (
                energy - power * self.params.time_step_hours / self.params.discharge_efficiency
                if power >= 0
                else energy + -power * self.params.time_step_hours * self.params.charge_efficiency
            )

            if next_energy < min_reachable_energy:
                power = (
                    (energy - min_reachable_energy) * self.params.discharge_efficiency / self.params.time_step_hours
                    if min_reachable_energy <= energy
                    else -(min_reachable_energy - energy) / (self.params.charge_efficiency * self.params.time_step_hours)
                )
            elif next_energy > max_reachable_energy:
                power = (
                    (energy - max_reachable_energy) * self.params.discharge_efficiency / self.params.time_step_hours
                    if max_reachable_energy <= energy
                    else -(max_reachable_energy - energy) / (self.params.charge_efficiency * self.params.time_step_hours)
                )

            power = float(np.clip(power, ramp_lower, ramp_upper))
            repaired[hour] = power
            energy = (
                energy - power * self.params.time_step_hours / self.params.discharge_efficiency
                if power >= 0
                else energy + -power * self.params.time_step_hours * self.params.charge_efficiency
            )

        return repaired

    def repair_diesel_power(self, raw_diesel_kw: np.ndarray, battery_kw: np.ndarray, wt_kw: np.ndarray, pv_kw: np.ndarray) -> np.ndarray:
        raw_diesel_kw = np.asarray(raw_diesel_kw, dtype=float)
        battery_charge_kw = np.maximum(-battery_kw, 0.0)
        battery_discharge_kw = np.maximum(battery_kw, 0.0)
        renewable_surplus_kw = self.renewable_surplus_profile(wt_kw, pv_kw)
        allowed_sell_kw = np.minimum(self.params.grid_sell_max_kw, np.maximum(renewable_surplus_kw - battery_charge_kw, 0.0))

        net_demand = self.profiles.load_kw - pv_kw - wt_kw - battery_kw
        lower = np.maximum(0.0, net_demand - self.params.grid_buy_max_kw)
        grid_upper = net_demand + self.params.grid_sell_max_kw
        surplus_upper = self.profiles.load_kw - wt_kw - pv_kw + battery_charge_kw + allowed_sell_kw - battery_discharge_kw
        upper = np.minimum.reduce([np.full(self.hours, self.params.diesel_max_kw), grid_upper, surplus_upper])

        diesel_kw = np.zeros(self.hours, dtype=float)
        for hour in range(self.hours):
            hour_upper = max(0.0, float(upper[hour]))
            hour_lower = min(max(0.0, float(lower[hour])), hour_upper)

            if hour_upper < self.params.diesel_min_kw or raw_diesel_kw[hour] < self.params.diesel_min_kw:
                target_power = 0.0
            else:
                hour_lower = max(hour_lower, self.params.diesel_min_kw)
                target_power = float(np.clip(raw_diesel_kw[hour], hour_lower, hour_upper))

            if hour > 0 and diesel_kw[hour - 1] >= self.params.diesel_min_kw and target_power >= self.params.diesel_min_kw:
                ramp_lower = diesel_kw[hour - 1] - self.params.diesel_ramp_down_limit_kw
                ramp_upper = diesel_kw[hour - 1] + self.params.diesel_ramp_up_limit_kw
                continuous_lower = max(hour_lower, self.params.diesel_min_kw, ramp_lower)
                continuous_upper = min(hour_upper, ramp_upper)
                if continuous_lower <= continuous_upper:
                    target_power = float(np.clip(target_power, continuous_lower, continuous_upper))
                else:
                    target_power = float(np.clip(target_power, hour_lower, hour_upper))

            diesel_kw[hour] = target_power
        return diesel_kw

    def battery_energy_profile(self, battery_kw: np.ndarray) -> np.ndarray:
        energy = np.empty(self.hours + 1, dtype=float)
        energy[0] = self.params.soc_initial * self.params.battery_capacity_kwh
        for hour, power in enumerate(battery_kw):
            energy[hour + 1] = (
                energy[hour] - power * self.params.time_step_hours / self.params.discharge_efficiency
                if power >= 0
                else energy[hour] + -power * self.params.time_step_hours * self.params.charge_efficiency
            )
        return energy

    def diesel_ramp_violations(self, diesel_kw: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
        consecutive_running = (diesel_kw[1:] >= self.params.diesel_min_kw) & (diesel_kw[:-1] >= self.params.diesel_min_kw)
        diesel_delta_kw = np.diff(diesel_kw)
        ramp_up_violation = np.where(consecutive_running, np.maximum(diesel_delta_kw - self.params.diesel_ramp_up_limit_kw, 0.0), 0.0)
        ramp_down_violation = np.where(consecutive_running, np.maximum(-diesel_delta_kw - self.params.diesel_ramp_down_limit_kw, 0.0), 0.0)
        return ramp_up_violation, ramp_down_violation

    def evaluate_dispatch(self, solution: np.ndarray) -> dict[str, Any]:
        raw_diesel_kw, raw_battery_kw, raw_wind_curtail_fraction, raw_pv_curtail_fraction = self.split_solution(solution)
        wind_curtail_kw, pv_curtail_kw, actual_wind_kw, actual_pv_kw = self.repair_curtailment(
            raw_wind_curtail_fraction,
            raw_pv_curtail_fraction,
        )
        battery_kw = self.repair_battery_power(raw_battery_kw, actual_wind_kw, actual_pv_kw)
        diesel_kw = self.repair_diesel_power(raw_diesel_kw, battery_kw, actual_wind_kw, actual_pv_kw)
        grid_kw = self.profiles.load_kw - actual_pv_kw - actual_wind_kw - diesel_kw - battery_kw
        energy_kwh = self.battery_energy_profile(battery_kw)
        soc = energy_kwh / self.params.battery_capacity_kwh

        buy_kw = np.maximum(grid_kw, 0.0)
        sell_kw = np.maximum(-grid_kw, 0.0)
        charge_kw = np.maximum(-battery_kw, 0.0)
        discharge_kw = np.maximum(battery_kw, 0.0)
        charge_state = (charge_kw > 1.0e-8).astype(float)
        renewable_surplus_kw = self.renewable_surplus_profile(actual_wind_kw, actual_pv_kw)
        diesel_power_square_kwh = np.sum(diesel_kw**2) * self.params.time_step_hours

        economic_diesel_unit = max(self.params.diesel_unit_cost, float(np.mean(self.profiles.buy_price)) * 1.35)
        economic_cost = float(
            economic_diesel_unit * np.sum(diesel_kw) * self.params.time_step_hours
            + (
                self.params.diesel_quadratic_fuel_cost * diesel_power_square_kwh
                if self.params.enable_extra_complexity
                else 0.0
            )
            + self.params.battery_om_cost * np.sum(np.abs(battery_kw)) * self.params.time_step_hours
            + np.sum(self.profiles.buy_price * buy_kw - self.profiles.sell_price * sell_kw) * self.params.time_step_hours
        )

        diesel_kwh = np.sum(diesel_kw) * self.params.time_step_hours
        grid_buy_kwh = np.sum(buy_kw) * self.params.time_step_hours
        grid_exchange_kwh = np.sum(buy_kw + sell_kw) * self.params.time_step_hours
        curtailment_kwh = np.sum(wind_curtail_kw + pv_curtail_kw) * self.params.time_step_hours
        diesel_environment_unit = float(np.dot(DIESEL_EMISSION_FACTOR, TREATMENT_COST))
        raw_grid_environment_unit = float(np.dot(GRID_EMISSION_FACTOR, TREATMENT_COST))
        grid_environment_unit = max(raw_grid_environment_unit * 4.0, diesel_environment_unit * 2.8)
        curtailment_environment_unit = max(grid_environment_unit, diesel_environment_unit) * 1.2
        environment_cost = float(
            diesel_environment_unit * diesel_kwh
            + (
                self.params.diesel_quadratic_emission_cost * diesel_power_square_kwh
                if self.params.enable_extra_complexity
                else 0.0
            )
            + grid_environment_unit * grid_buy_kwh
            + curtailment_environment_unit * curtailment_kwh
            + 0.01 * grid_exchange_kwh
        )

        ramp_up_violation, ramp_down_violation = self.diesel_ramp_violations(diesel_kw)
        battery_ramp_violation = (
            np.maximum(np.abs(np.diff(battery_kw)) - self.params.battery_ramp_limit_kw, 0.0)
            if self.params.enable_extra_complexity
            else np.zeros(max(0, len(battery_kw) - 1), dtype=float)
        )
        grid_exchange_ramp_violation = (
            np.maximum(np.abs(np.diff(grid_kw)) - self.params.grid_exchange_ramp_limit_kw, 0.0)
            if self.params.enable_extra_complexity
            else np.zeros(max(0, len(grid_kw) - 1), dtype=float)
        )
        charge_bound_violation = np.maximum(charge_kw - charge_state * self.params.battery_charge_max_kw, 0.0)
        discharge_bound_violation = np.maximum(discharge_kw - (1.0 - charge_state) * self.params.battery_discharge_max_kw, 0.0)
        simultaneous_violation = np.minimum(charge_kw, discharge_kw)

        penalty = 0.0
        penalty += np.sum(np.maximum(buy_kw - self.params.grid_buy_max_kw, 0.0) ** 2)
        penalty += np.sum(np.maximum(sell_kw - self.params.grid_sell_max_kw, 0.0) ** 2)
        penalty += np.sum(np.maximum(sell_kw + charge_kw - renewable_surplus_kw, 0.0) ** 2)
        penalty += np.sum(ramp_up_violation**2)
        penalty += np.sum(ramp_down_violation**2)
        penalty += np.sum(battery_ramp_violation**2)
        penalty += np.sum(grid_exchange_ramp_violation**2)
        penalty += np.sum(charge_bound_violation**2)
        penalty += np.sum(discharge_bound_violation**2)
        penalty += np.sum(simultaneous_violation**2)
        penalty += (soc[-1] - self.params.soc_initial) ** 2
        penalty_value = float(self.params.penalty_weight * penalty)

        return {
            "time_h": (np.arange(self.hours, dtype=float) + 1.0) * self.params.time_step_hours,
            "load_kw": self.profiles.load_kw.copy(),
            "pv_available_kw": self.profiles.pv_kw.copy(),
            "wind_available_kw": self.profiles.wt_kw.copy(),
            "pv_kw": actual_pv_kw,
            "wt_kw": actual_wind_kw,
            "diesel_kw": diesel_kw,
            "battery_kw": battery_kw,
            "battery_charge_kw": charge_kw,
            "battery_discharge_kw": discharge_kw,
            "battery_charge_state": charge_state,
            "grid_kw": grid_kw,
            "grid_buy_kw": buy_kw,
            "grid_sell_kw": sell_kw,
            "buy_price": self.profiles.buy_price.copy(),
            "sell_price": self.profiles.sell_price.copy(),
            "wind_curtail_kw": wind_curtail_kw,
            "pv_curtail_kw": pv_curtail_kw,
            "total_curtail_kw": wind_curtail_kw + pv_curtail_kw,
            "renewable_surplus_kw": renewable_surplus_kw,
            "diesel_ramp_up_violation_kw": np.concatenate([[0.0], ramp_up_violation]),
            "diesel_ramp_down_violation_kw": np.concatenate([[0.0], ramp_down_violation]),
            "battery_ramp_violation_kw": np.concatenate([[0.0], battery_ramp_violation]),
            "grid_exchange_ramp_violation_kw": np.concatenate([[0.0], grid_exchange_ramp_violation]),
            "battery_charge_bound_violation_kw": charge_bound_violation,
            "battery_discharge_bound_violation_kw": discharge_bound_violation,
            "battery_mutual_exclusion_violation_kw": simultaneous_violation,
            "energy_kwh": energy_kwh,
            "soc": soc,
            "economic_cost": economic_cost,
            "environment_cost": environment_cost,
            "penalty": penalty_value,
        }

    def objective_function(self, solution: np.ndarray) -> np.ndarray:
        dispatch = self.evaluate_dispatch(solution)
        penalty = dispatch["penalty"]
        return np.array([dispatch["economic_cost"] + penalty, dispatch["environment_cost"] + penalty], dtype=float)


def canonical_column_name(name: str) -> str | None:
    normalized = str(name).strip().lower().replace(" ", "").replace("-", "_")
    for canonical, aliases in COLUMN_ALIASES.items():
        if normalized in {alias.lower().replace(" ", "").replace("-", "_") for alias in aliases}:
            return canonical
    return None


def parse_float(value: Any) -> float | None:
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None
    try:
        return float(text)
    except ValueError:
        return None


def read_csv_rows(path: Path) -> list[list[str]]:
    encodings = ["utf-8-sig", "utf-8", "gbk"]
    last_error: UnicodeDecodeError | None = None
    for encoding in encodings:
        try:
            with open(path, newline="", encoding=encoding) as file:
                return [row for row in csv.reader(file) if any(str(cell).strip() for cell in row)]
        except UnicodeDecodeError as exc:
            last_error = exc
    raise ValueError(f"Cannot decode CSV file: {path}") from last_error


def read_xlsx_rows(path: Path) -> list[list[str]]:
    namespace = {"main": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
    with zipfile.ZipFile(path) as archive:
        shared_strings: list[str] = []
        if "xl/sharedStrings.xml" in archive.namelist():
            root = ElementTree.fromstring(archive.read("xl/sharedStrings.xml"))
            for item in root.findall("main:si", namespace):
                texts = [node.text or "" for node in item.findall(".//main:t", namespace)]
                shared_strings.append("".join(texts))

        workbook = ElementTree.fromstring(archive.read("xl/workbook.xml"))
        first_sheet = workbook.find("main:sheets/main:sheet", namespace)
        if first_sheet is None:
            raise ValueError(f"No sheet found in XLSX file: {path}")
        relation_id = first_sheet.attrib["{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"]

        rels_root = ElementTree.fromstring(archive.read("xl/_rels/workbook.xml.rels"))
        rel_namespace = {"rel": "http://schemas.openxmlformats.org/package/2006/relationships"}
        target = None
        for rel in rels_root.findall("rel:Relationship", rel_namespace):
            if rel.attrib.get("Id") == relation_id:
                target = rel.attrib["Target"]
                break
        if target is None:
            raise ValueError(f"Cannot resolve first sheet in XLSX file: {path}")
        sheet_path = "xl/" + target.lstrip("/")
        if sheet_path not in archive.namelist():
            sheet_path = "xl/worksheets/" + Path(target).name

        sheet_root = ElementTree.fromstring(archive.read(sheet_path))
        rows: list[list[str]] = []
        for row in sheet_root.findall(".//main:sheetData/main:row", namespace):
            values_by_index: dict[int, str] = {}
            for cell in row.findall("main:c", namespace):
                ref = cell.attrib.get("r", "")
                column_letters = "".join(ch for ch in ref if ch.isalpha())
                column_index = 0
                for ch in column_letters:
                    column_index = column_index * 26 + ord(ch.upper()) - ord("A") + 1
                column_index = max(column_index - 1, len(values_by_index))
                value_node = cell.find("main:v", namespace)
                inline_text = cell.find("main:is/main:t", namespace)
                if cell.attrib.get("t") == "s" and value_node is not None:
                    value = shared_strings[int(value_node.text or "0")]
                elif inline_text is not None:
                    value = inline_text.text or ""
                elif value_node is not None:
                    value = value_node.text or ""
                else:
                    value = ""
                values_by_index[column_index] = value
            if values_by_index:
                max_index = max(values_by_index)
                rows.append([values_by_index.get(index, "") for index in range(max_index + 1)])
        return [row for row in rows if any(str(cell).strip() for cell in row)]


def read_table_rows(path: str | Path) -> list[list[str]]:
    input_path = Path(path)
    suffix = input_path.suffix.lower()
    if suffix == ".csv":
        return read_csv_rows(input_path)
    if suffix in {".xlsx", ".xlsm"}:
        return read_xlsx_rows(input_path)
    raise ValueError(f"Unsupported input file type: {input_path.suffix}. Use CSV or XLSX.")


def rows_to_profile_columns(rows: list[list[str]]) -> dict[str, list[float]]:
    if not rows:
        raise ValueError("Input profile file is empty.")

    first_row_numbers = [parse_float(cell) for cell in rows[0]]
    has_header = any(value is None for value in first_row_numbers)

    columns: dict[str, list[float]] = {}
    if has_header:
        headers = rows[0]
        canonical_names = [canonical_column_name(header) for header in headers]
        for row in rows[1:]:
            for index, canonical_name in enumerate(canonical_names):
                if canonical_name is None or canonical_name == "time":
                    continue
                value = parse_float(row[index] if index < len(row) else None)
                if value is not None:
                    columns.setdefault(canonical_name, []).append(value)
    else:
        numeric_rows = [[value for value in (parse_float(cell) for cell in row) if value is not None] for row in rows]
        numeric_rows = [row for row in numeric_rows if row]
        if not numeric_rows:
            raise ValueError("Input profile file does not contain numeric data.")
        width = max(len(row) for row in numeric_rows)
        if width == 1:
            columns["buy_price"] = [row[0] for row in numeric_rows]
        else:
            default_order = ["time", "buy_price", "sell_price", "load_kw", "pv_kw", "wt_kw"]
            for column_index in range(min(width, len(default_order))):
                canonical_name = default_order[column_index]
                if canonical_name == "time":
                    continue
                values = [row[column_index] for row in numeric_rows if len(row) > column_index]
                if values:
                    columns[canonical_name] = values

    if not columns:
        raise ValueError("No supported profile columns found. Supported: buy_price, sell_price, load_kw, pv_kw, wt_kw.")
    return columns


def fit_daily_profile(values: list[float] | np.ndarray, target_periods: int, profile_kind: str) -> np.ndarray:
    values = np.asarray(values, dtype=float)
    if values.size == target_periods:
        return values.copy()
    if values.size == 24:
        if profile_kind in {"load_kw", "pv_kw", "wt_kw"} and target_periods == 48:
            return expand_hourly_profile_with_midpoints(values)
        return expand_hourly_profile(values, DEFAULT_TIME_STEP_HOURS)
    raise ValueError(f"{profile_kind} expects 24 hourly or {target_periods} half-hour values, got {values.size}.")


def load_daily_profiles(profile_path: str | Path | None = None) -> DailyProfiles:
    profiles = default_daily_profiles()
    if profile_path is None:
        return profiles

    columns = rows_to_profile_columns(read_table_rows(profile_path))
    target_periods = profiles.periods
    data = {
        "load_kw": profiles.load_kw,
        "pv_kw": profiles.pv_kw,
        "wt_kw": profiles.wt_kw,
        "buy_price": profiles.buy_price,
        "sell_price": profiles.sell_price,
    }
    for name, values in columns.items():
        data[name] = fit_daily_profile(values, target_periods, name)

    if "buy_price" in columns and "sell_price" not in columns:
        default_ratio = np.divide(profiles.sell_price, profiles.buy_price, out=np.full(target_periods, 0.6), where=profiles.buy_price != 0)
        data["sell_price"] = data["buy_price"] * default_ratio

    return DailyProfiles(
        load_kw=data["load_kw"],
        pv_kw=data["pv_kw"],
        wt_kw=data["wt_kw"],
        buy_price=data["buy_price"],
        sell_price=data["sell_price"],
    )


def load_microgrid_params(params_path: str | Path | None, time_step_hours: float = DEFAULT_TIME_STEP_HOURS) -> MicrogridParams:
    if params_path is None:
        return MicrogridParams(time_step_hours=time_step_hours)

    path = Path(params_path)
    if not path.exists():
        raise FileNotFoundError(f"Microgrid params file not found: {path}")

    data = json.loads(path.read_text(encoding="utf-8-sig"))
    base = MicrogridParams(time_step_hours=time_step_hours)

    def value(name: str, default: float) -> float:
        raw = data.get(name, default)
        return float(raw if raw is not None else default)

    turbine_unit_cost = value("microTurbineUnitCost", base.diesel_unit_cost)
    return MicrogridParams(
        time_step_hours=time_step_hours,
        diesel_min_kw=value("microTurbineMinKw", base.diesel_min_kw),
        diesel_max_kw=value("microTurbineMaxKw", base.diesel_max_kw),
        diesel_ramp_up_kw_per_h=value("microTurbineRampUpKw", base.diesel_ramp_up_kw_per_h),
        diesel_ramp_down_kw_per_h=value("microTurbineRampDownKw", base.diesel_ramp_down_kw_per_h),
        battery_capacity_kwh=value("batteryCapacityKwh", base.battery_capacity_kwh),
        battery_charge_max_kw=value("batteryChargeMaxKw", base.battery_charge_max_kw),
        battery_discharge_max_kw=value("batteryDischargeMaxKw", base.battery_discharge_max_kw),
        soc_min=value("batterySocMin", base.soc_min),
        soc_max=value("batterySocMax", base.soc_max),
        soc_initial=value("batterySocInitial", base.soc_initial),
        grid_buy_max_kw=value("gridBuyMaxKw", base.grid_buy_max_kw),
        grid_sell_max_kw=value("gridSellMaxKw", base.grid_sell_max_kw),
        diesel_om_cost=0.0,
        diesel_fuel_cost=turbine_unit_cost,
        battery_om_cost=value("batteryOmCost", base.battery_om_cost),
    )


def select_compromise_index(objectives: np.ndarray) -> int:
    objectives = np.asarray(objectives, dtype=float)
    ideal = np.min(objectives, axis=0)
    nadir = np.max(objectives, axis=0)
    span = np.where(np.isclose(nadir - ideal, 0.0), 1.0, nadir - ideal)
    normalized = (objectives - ideal) / span
    return int(np.argmin(np.linalg.norm(normalized, axis=1)))


def calculate_dispatch_metrics(dispatch: dict[str, Any], profiles: DailyProfiles) -> dict[str, float]:
    total_available_renewable_kwh = float(
        np.sum(dispatch["wind_available_kw"] + dispatch["pv_available_kw"]) * profiles.time_step_hours
    )
    total_curtailment_kwh = float(np.sum(dispatch["total_curtail_kw"]) * profiles.time_step_hours)
    renewable_utilization_rate = (
        1.0 - total_curtailment_kwh / total_available_renewable_kwh
        if total_available_renewable_kwh > 0.0
        else 1.0
    )
    return {
        "economicCost": float(dispatch["economic_cost"]),
        "environmentCost": float(dispatch["environment_cost"]),
        "penalty": float(dispatch["penalty"]),
        "finalSoc": float(dispatch["soc"][-1]),
        "totalCurtailmentKwh": total_curtailment_kwh,
        "renewableUtilizationRate": float(renewable_utilization_rate),
    }


def build_solution_options(
    archive_objectives: np.ndarray,
    archive_dispatches: list[dict[str, Any]],
    profiles: DailyProfiles,
    compromise_index: int,
) -> list[dict[str, Any]]:
    economic_index = int(np.argmin(archive_objectives[:, 0]))
    environment_index = int(np.argmin(archive_objectives[:, 1]))
    definitions = [
        (
            "balanced",
            "综合折中方案",
            "同时考虑经济成本和环境成本，选择归一化后距离理想点最近的 Pareto 解。",
            compromise_index,
        ),
        (
            "economic_min",
            "经济成本最低方案",
            "只按经济目标排序，不额外考虑环境成本。",
            economic_index,
        ),
        (
            "environment_min",
            "环境成本最低方案",
            "只按环境目标排序，不额外考虑经济成本。",
            environment_index,
        ),
    ]

    options = []
    for key, label, description, index in definitions:
        dispatch = archive_dispatches[index]
        metrics = calculate_dispatch_metrics(dispatch, profiles)
        objectives = archive_objectives[index]
        options.append(
            {
                "key": key,
                "label": label,
                "description": description,
                "paretoIndex": index + 1,
                "isCompromise": index == compromise_index,
                "penalizedEconomicObjective": float(objectives[0]),
                "penalizedEnvironmentObjective": float(objectives[1]),
                **metrics,
                "curves": build_curve_rows(dispatch),
            }
        )
    return options


def array_to_list(values: Any) -> Any:
    if isinstance(values, np.ndarray):
        return values.astype(float).tolist()
    if isinstance(values, np.generic):
        return values.item()
    return values


def build_curve_rows(dispatch: dict[str, Any]) -> list[dict[str, float]]:
    rows = []
    period_count = len(dispatch["load_kw"])
    for index in range(period_count):
        rows.append(
            {
                "period": index + 1,
                "time_h": float(dispatch["time_h"][index]),
                "load_kw": float(dispatch["load_kw"][index]),
                "wind_available_kw": float(dispatch["wind_available_kw"][index]),
                "pv_available_kw": float(dispatch["pv_available_kw"][index]),
                "wind_kw": float(dispatch["wt_kw"][index]),
                "pv_kw": float(dispatch["pv_kw"][index]),
                "diesel_kw": float(dispatch["diesel_kw"][index]),
                "battery_kw": float(dispatch["battery_kw"][index]),
                "battery_charge_kw": float(dispatch["battery_charge_kw"][index]),
                "battery_discharge_kw": float(dispatch["battery_discharge_kw"][index]),
                "grid_net_kw": float(dispatch["grid_kw"][index]),
                "grid_buy_kw": float(dispatch["grid_buy_kw"][index]),
                "grid_sell_kw": float(dispatch["grid_sell_kw"][index]),
                "buy_price": float(dispatch["buy_price"][index]),
                "sell_price": float(dispatch["sell_price"][index]),
                "soc": float(dispatch["soc"][index + 1]),
                "total_curtail_kw": float(dispatch["total_curtail_kw"][index]),
            }
        )
    return rows


def run_moiabc_dispatch(
    profile_path: str | Path | None = None,
    params_path: str | Path | None = None,
    config: MoiabcRunConfig | None = None,
) -> dict[str, Any]:
    config = config or MoiabcRunConfig()
    profiles = load_daily_profiles(profile_path)
    params = load_microgrid_params(params_path, profiles.time_step_hours)
    model = MicrogridDispatchModel(profiles, params)

    start = time.perf_counter()
    archive_solutions, archive_objectives, history, used_seed = MOIABC.multi_objective_iabc(
        model.objective_function,
        model.bounds,
        bee=config.bee,
        max_iter=config.max_iter,
        limit=config.limit,
        tournament_size=config.tournament_size,
        elite_rate=config.elite_rate,
        elimination_rate=config.elimination_rate,
        archive_size=config.archive_size,
        archive_guidance_rate=config.archive_guidance_rate,
        seed=config.seed,
    )
    elapsed_seconds = time.perf_counter() - start

    compromise_index = select_compromise_index(archive_objectives)
    archive_dispatches = [model.evaluate_dispatch(solution) for solution in archive_solutions]
    compromise_solution = archive_solutions[compromise_index]
    compromise_objectives = archive_objectives[compromise_index]
    dispatch = archive_dispatches[compromise_index]
    compromise_metrics = calculate_dispatch_metrics(dispatch, profiles)
    solution_options = build_solution_options(archive_objectives, archive_dispatches, profiles, compromise_index)

    pareto_front = [
        {
            "index": index + 1,
            "key": f"pareto_{index + 1}",
            "label": f"Pareto 方案 {index + 1}",
            "description": "Pareto 前沿上的可选调度方案。",
            "paretoIndex": index + 1,
            "is_compromise": index == compromise_index,
            "isCompromise": index == compromise_index,
            "is_economic_best": any(
                option["key"] == "economic_min" and option["paretoIndex"] == index + 1 for option in solution_options
            ),
            "isEconomicBest": any(
                option["key"] == "economic_min" and option["paretoIndex"] == index + 1 for option in solution_options
            ),
            "is_environment_best": any(
                option["key"] == "environment_min" and option["paretoIndex"] == index + 1 for option in solution_options
            ),
            "isEnvironmentBest": any(
                option["key"] == "environment_min" and option["paretoIndex"] == index + 1 for option in solution_options
            ),
            "economic_cost": float(dispatch_result["economic_cost"]),
            "economicCost": float(dispatch_result["economic_cost"]),
            "environment_cost": float(dispatch_result["environment_cost"]),
            "environmentCost": float(dispatch_result["environment_cost"]),
            "penalized_economic_objective": float(objectives[0]),
            "penalizedEconomicObjective": float(objectives[0]),
            "penalized_environment_objective": float(objectives[1]),
            "penalizedEnvironmentObjective": float(objectives[1]),
            **calculate_dispatch_metrics(dispatch_result, profiles),
            "curves": build_curve_rows(dispatch_result),
        }
        for index, (dispatch_result, objectives) in enumerate(zip(archive_dispatches, archive_objectives))
    ]

    return {
        "summary": {
            "algorithm": "MOIABC",
            "input_file": str(profile_path) if profile_path is not None else None,
            "periods": profiles.periods,
            "time_step_hours": profiles.time_step_hours,
            "decision_variables": len(model.bounds),
            "archive_size": int(len(archive_objectives)),
            "seed": int(used_seed),
            "elapsed_seconds": float(elapsed_seconds),
            "config": asdict(config),
            "microgrid_params": asdict(model.params),
            "compromise_index": compromise_index + 1,
            "compromise_economic_cost": compromise_metrics["economicCost"],
            "compromise_environment_cost": compromise_metrics["environmentCost"],
            "compromise_penalty": compromise_metrics["penalty"],
            "compromise_penalized_economic_objective": float(compromise_objectives[0]),
            "compromise_penalized_environment_objective": float(compromise_objectives[1]),
            "final_soc": compromise_metrics["finalSoc"],
            "total_curtailment_kwh": compromise_metrics["totalCurtailmentKwh"],
            "renewable_utilization_rate": compromise_metrics["renewableUtilizationRate"],
        },
        "curves": build_curve_rows(dispatch),
        "solution_options": solution_options,
        "pareto_front": pareto_front,
        "convergence": [{"iteration": index, "best_objective_sum": float(value)} for index, value in enumerate(history)],
        "raw": {
            "compromise_solution": array_to_list(compromise_solution),
            "compromise_dispatch": {key: array_to_list(value) for key, value in dispatch.items()},
        },
    }


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if not rows:
        path.write_text("", encoding="utf-8-sig")
        return
    with open(path, "w", newline="", encoding="utf-8-sig") as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)


def save_business_result(result: dict[str, Any], output_dir: str | Path) -> None:
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    (output_path / "summary.json").write_text(json.dumps(result["summary"], ensure_ascii=False, indent=2), encoding="utf-8")
    (output_path / "dispatch_curves.json").write_text(json.dumps(result["curves"], ensure_ascii=False, indent=2), encoding="utf-8")
    (output_path / "solution_options.json").write_text(json.dumps(result["solution_options"], ensure_ascii=False, indent=2), encoding="utf-8")
    (output_path / "pareto_front.json").write_text(json.dumps(result["pareto_front"], ensure_ascii=False, indent=2), encoding="utf-8")
    (output_path / "convergence.json").write_text(json.dumps(result["convergence"], ensure_ascii=False, indent=2), encoding="utf-8")
    (output_path / "result_all.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    write_csv(output_path / "dispatch_curves.csv", result["curves"])
    for option in result["solution_options"]:
        write_csv(output_path / f"dispatch_curves_{option['key']}.csv", option["curves"])
    write_csv(output_path / "pareto_front.csv", result["pareto_front"])
    write_csv(output_path / "convergence.csv", result["convergence"])


def write_input_template(path: str | Path) -> None:
    profiles = default_daily_profiles()
    rows = []
    for hour in range(24):
        period = hour * 2
        rows.append(
            {
                "hour": hour,
                "buy_price": float(profiles.buy_price[period]),
                "sell_price": float(profiles.sell_price[period]),
                "load_kw": float(BASE_LOAD_KW[hour]),
                "pv_kw": float(BASE_PV_KW[hour]),
                "wt_kw": float(BASE_WT_KW[hour]),
            }
        )
    write_csv(Path(path), rows)


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Run MOIABC microgrid daily dispatch business service.")
    parser.add_argument("--input", help="CSV/XLSX profile file. If omitted, built-in default daily profiles are used.")
    parser.add_argument("--output", default=str(Path(__file__).resolve().parent / "results"), help="Output directory.")
    parser.add_argument("--params", help="JSON microgrid equipment parameter file.")
    parser.add_argument("--template", help="Write a CSV input template and exit.")
    parser.add_argument("--bee", type=int, default=MoiabcRunConfig.bee)
    parser.add_argument("--max-iter", type=int, default=MoiabcRunConfig.max_iter)
    parser.add_argument("--limit", type=int, default=MoiabcRunConfig.limit)
    parser.add_argument("--archive-size", type=int, default=MoiabcRunConfig.archive_size)
    parser.add_argument("--tournament-size", type=int, default=MoiabcRunConfig.tournament_size)
    parser.add_argument("--elite-rate", type=float, default=MoiabcRunConfig.elite_rate)
    parser.add_argument("--elimination-rate", type=float, default=MoiabcRunConfig.elimination_rate)
    parser.add_argument("--archive-guidance-rate", type=float, default=MoiabcRunConfig.archive_guidance_rate)
    parser.add_argument("--seed", type=int, default=MoiabcRunConfig.seed)
    return parser


def main(argv: list[str] | None = None) -> None:
    parser = build_arg_parser()
    args = parser.parse_args(argv)
    if args.template:
        write_input_template(args.template)
        print(f"Template written: {args.template}")
        return

    config = MoiabcRunConfig(
        bee=args.bee,
        max_iter=args.max_iter,
        limit=args.limit,
        archive_size=args.archive_size,
        tournament_size=args.tournament_size,
        elite_rate=args.elite_rate,
        elimination_rate=args.elimination_rate,
        archive_guidance_rate=args.archive_guidance_rate,
        seed=args.seed,
    )
    print(f"MG_PROGRESS=0/{config.max_iter}", flush=True)
    result = run_moiabc_dispatch(args.input, args.params, config)
    print(f"MG_PROGRESS={config.max_iter}/{config.max_iter}", flush=True)
    save_business_result(result, args.output)
    summary = result["summary"]
    print("MOIABC microgrid dispatch finished")
    print(f"Output: {Path(args.output).resolve()}")
    print(f"Archive size: {summary['archive_size']}")
    print(f"Compromise economic cost: {summary['compromise_economic_cost']:.6f}")
    print(f"Compromise environment cost: {summary['compromise_environment_cost']:.6f}")
    print(f"Penalty: {summary['compromise_penalty']:.6f}")


if __name__ == "__main__":
    main()
