# -*- coding: utf-8 -*-
"""Microgrid dispatch model for the multi-objective application case."""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np


TIME_STEP_HOURS = 0.5
BASE_HOURS = 24

BASE_LOAD_KW = np.array(
    [92, 88, 80, 76, 78, 90, 108, 126, 136, 148, 142, 138,
     134, 140, 148, 160, 176, 196, 205, 198, 174, 150, 126, 104],
    dtype=float,
)
BASE_PV_KW = np.array(
    [0, 0, 0, 0, 0, 8, 18, 42, 72, 98, 112, 120,
     118, 106, 84, 56, 28, 10, 0, 0, 0, 0, 0, 0],
    dtype=float,
)
BASE_WT_KW = np.array(
    [66, 64, 68, 70, 72, 76, 74, 70, 66, 62, 58, 54,
     50, 48, 46, 44, 48, 56, 62, 70, 76, 78, 74, 70],
    dtype=float,
)

BASE_BUY_PRICE = np.array(
    [0.42, 0.42, 0.42, 0.42, 0.42, 0.42, 0.52, 0.52, 0.52, 0.60, 0.60, 0.60,
     0.60, 0.60, 0.60, 0.52, 0.52, 0.60, 0.60, 0.60, 0.52, 0.52, 0.52, 0.42],
    dtype=float,
)
BASE_SELL_PRICE = np.array(
    [0.25, 0.25, 0.25, 0.25, 0.25, 0.25, 0.32, 0.32, 0.32, 0.38, 0.38, 0.38,
     0.38, 0.38, 0.38, 0.32, 0.32, 0.38, 0.38, 0.38, 0.32, 0.32, 0.32, 0.25],
    dtype=float,
)


def expand_hourly_profile(values, time_step_hours=TIME_STEP_HOURS):
    repeat_count = int(round(1.0 / time_step_hours))
    return np.repeat(np.asarray(values, dtype=float), repeat_count)


def expand_hourly_profile_with_midpoints(values):
    values = np.asarray(values, dtype=float)
    expanded = np.empty(values.size * 2, dtype=float)
    expanded[0::2] = values
    expanded[1::2] = 0.5 * (values + np.roll(values, -1))
    return expanded


LOAD_KW = expand_hourly_profile_with_midpoints(BASE_LOAD_KW)
PV_KW = expand_hourly_profile_with_midpoints(BASE_PV_KW)
WT_KW = expand_hourly_profile_with_midpoints(BASE_WT_KW)
BUY_PRICE = expand_hourly_profile(BASE_BUY_PRICE)
SELL_PRICE = expand_hourly_profile(BASE_SELL_PRICE)
HOURS = len(LOAD_KW)

DIESEL_EMISSION_FACTOR = np.array([1.120, 0.0050, 0.0022], dtype=float)
GRID_EMISSION_FACTOR = np.array([0.997, 0.0045, 0.0018], dtype=float)
TREATMENT_COST = np.array([0.023, 6.0, 8.0], dtype=float)


@dataclass(frozen=True)
class MicrogridParams:
    time_step_hours: float = TIME_STEP_HOURS
    enable_curtailment: bool = False
    enable_extra_complexity: bool = False
    diesel_min_kw: float = 20.0
    diesel_max_kw: float = 160.0
    diesel_ramp_up_kw_per_h: float = 55.0
    diesel_ramp_down_kw_per_h: float = 55.0
    diesel_quadratic_fuel_cost: float = 0.0015
    diesel_quadratic_emission_cost: float = 0.00035
    battery_charge_max_kw: float = 90.0
    battery_discharge_max_kw: float = 90.0
    battery_ramp_kw_per_h: float = 90.0
    grid_buy_max_kw: float = 240.0
    grid_sell_max_kw: float = 160.0
    grid_exchange_ramp_kw_per_h: float = 120.0
    battery_capacity_kwh: float = 360.0
    soc_initial: float = 0.5
    soc_min: float = 0.2
    soc_max: float = 0.9
    charge_efficiency: float = 0.95
    discharge_efficiency: float = 0.95
    diesel_om_cost: float = 0.05
    diesel_fuel_cost: float = 0.45
    battery_om_cost: float = 0.02
    penalty_weight: float = 1.0e5

    @property
    def diesel_unit_cost(self) -> float:
        return self.diesel_om_cost + self.diesel_fuel_cost

    @property
    def diesel_ramp_up_limit_kw(self) -> float:
        return self.diesel_ramp_up_kw_per_h * self.time_step_hours

    @property
    def diesel_ramp_down_limit_kw(self) -> float:
        return self.diesel_ramp_down_kw_per_h * self.time_step_hours

    @property
    def battery_ramp_limit_kw(self) -> float:
        return self.battery_ramp_kw_per_h * self.time_step_hours

    @property
    def grid_exchange_ramp_limit_kw(self) -> float:
        return self.grid_exchange_ramp_kw_per_h * self.time_step_hours


PARAMS = MicrogridParams()
BOUNDS = (
    [(0.0, PARAMS.diesel_max_kw)] * HOURS
    + [(-PARAMS.battery_charge_max_kw, PARAMS.battery_discharge_max_kw)] * HOURS
)
if PARAMS.enable_curtailment:
    BOUNDS = BOUNDS + [(0.0, 1.0)] * HOURS + [(0.0, 1.0)] * HOURS


def split_solution(solution):
    values = np.asarray(solution, dtype=float)
    if values.size == 2 * HOURS:
        zeros = np.zeros(HOURS, dtype=float)
        return values[:HOURS], values[HOURS:], zeros, zeros
    if values.size == 4 * HOURS:
        return values[:HOURS], values[HOURS:2 * HOURS], values[2 * HOURS:3 * HOURS], values[3 * HOURS:]
    raise ValueError(f"Expected {2 * HOURS} or {4 * HOURS} decision variables, got {values.size}.")


def renewable_surplus_profile(wt_kw=None, pv_kw=None):
    wt_kw = WT_KW if wt_kw is None else np.asarray(wt_kw, dtype=float)
    pv_kw = PV_KW if pv_kw is None else np.asarray(pv_kw, dtype=float)
    return np.maximum(wt_kw + pv_kw - LOAD_KW, 0.0)


def repair_curtailment(raw_wind_curtail_fraction, raw_pv_curtail_fraction, params=PARAMS):
    if not params.enable_curtailment:
        zeros = np.zeros(HOURS, dtype=float)
        return zeros, zeros, WT_KW.copy(), PV_KW.copy()

    wind_curtail_fraction = np.clip(np.asarray(raw_wind_curtail_fraction, dtype=float), 0.0, 1.0)
    pv_curtail_fraction = np.clip(np.asarray(raw_pv_curtail_fraction, dtype=float), 0.0, 1.0)
    wind_curtail_kw = wind_curtail_fraction * WT_KW
    pv_curtail_kw = pv_curtail_fraction * PV_KW
    actual_wind_kw = WT_KW - wind_curtail_kw
    actual_pv_kw = PV_KW - pv_curtail_kw
    return wind_curtail_kw, pv_curtail_kw, actual_wind_kw, actual_pv_kw


def repair_battery_power(raw_battery_kw, wt_kw=None, pv_kw=None, params=PARAMS):
    repaired = np.zeros(HOURS, dtype=float)
    energy = params.soc_initial * params.battery_capacity_kwh
    min_energy = params.soc_min * params.battery_capacity_kwh
    max_energy = params.soc_max * params.battery_capacity_kwh
    initial_energy = energy
    renewable_surplus_kw = renewable_surplus_profile(wt_kw, pv_kw)

    for hour in range(HOURS):
        remaining_hours = HOURS - hour - 1
        requested_power = raw_battery_kw[hour]

        max_discharge = min(
            params.battery_discharge_max_kw,
            max(0.0, (energy - min_energy) * params.discharge_efficiency / params.time_step_hours),
        )
        max_charge = min(
            params.battery_charge_max_kw,
            max(0.0, (max_energy - energy) / (params.charge_efficiency * params.time_step_hours)),
            renewable_surplus_kw[hour],
        )
        ramp_lower = -max_charge
        ramp_upper = max_discharge
        if params.enable_extra_complexity and hour > 0:
            ramp_lower = max(ramp_lower, repaired[hour - 1] - params.battery_ramp_limit_kw)
            ramp_upper = min(ramp_upper, repaired[hour - 1] + params.battery_ramp_limit_kw)
        power = float(np.clip(requested_power, -max_charge, max_discharge))
        power = float(np.clip(power, ramp_lower, ramp_upper))

        future_charge_capacity = float(
            np.sum(np.minimum(params.battery_charge_max_kw, renewable_surplus_kw[hour + 1:]))
            * params.time_step_hours
            * params.charge_efficiency
        )
        min_reachable_energy = max(
            min_energy,
            initial_energy - future_charge_capacity,
        )
        max_reachable_energy = min(
            max_energy,
            initial_energy + remaining_hours * params.battery_discharge_max_kw * params.time_step_hours / params.discharge_efficiency,
        )
        next_energy = (
            energy - power * params.time_step_hours / params.discharge_efficiency
            if power >= 0
            else energy + -power * params.time_step_hours * params.charge_efficiency
        )

        if next_energy < min_reachable_energy:
            power = (
                (energy - min_reachable_energy) * params.discharge_efficiency / params.time_step_hours
                if min_reachable_energy <= energy
                else -(min_reachable_energy - energy) / (params.charge_efficiency * params.time_step_hours)
            )
        elif next_energy > max_reachable_energy:
            power = (
                (energy - max_reachable_energy) * params.discharge_efficiency / params.time_step_hours
                if max_reachable_energy <= energy
                else -(max_reachable_energy - energy) / (params.charge_efficiency * params.time_step_hours)
            )

        power = float(np.clip(power, ramp_lower, ramp_upper))
        repaired[hour] = power
        energy = (
            energy - power * params.time_step_hours / params.discharge_efficiency
            if power >= 0
            else energy + -power * params.time_step_hours * params.charge_efficiency
        )

    return repaired


def repair_diesel_power(raw_diesel_kw, battery_kw, wt_kw=None, pv_kw=None, params=PARAMS):
    raw_diesel_kw = np.asarray(raw_diesel_kw, dtype=float)
    wt_kw = WT_KW if wt_kw is None else np.asarray(wt_kw, dtype=float)
    pv_kw = PV_KW if pv_kw is None else np.asarray(pv_kw, dtype=float)
    battery_charge_kw = np.maximum(-battery_kw, 0.0)
    battery_discharge_kw = np.maximum(battery_kw, 0.0)
    renewable_surplus_kw = renewable_surplus_profile(wt_kw, pv_kw)
    allowed_sell_kw = np.minimum(
        params.grid_sell_max_kw,
        np.maximum(renewable_surplus_kw - battery_charge_kw, 0.0),
    )

    net_demand = LOAD_KW - pv_kw - wt_kw - battery_kw
    lower = np.maximum(0.0, net_demand - params.grid_buy_max_kw)
    grid_upper = net_demand + params.grid_sell_max_kw
    surplus_upper = LOAD_KW - wt_kw - pv_kw + battery_charge_kw + allowed_sell_kw - battery_discharge_kw
    upper = np.minimum.reduce([np.full(HOURS, params.diesel_max_kw), grid_upper, surplus_upper])

    diesel_kw = np.zeros(HOURS, dtype=float)
    for hour in range(HOURS):
        hour_upper = max(0.0, float(upper[hour]))
        hour_lower = min(max(0.0, float(lower[hour])), hour_upper)

        if hour_upper < params.diesel_min_kw or raw_diesel_kw[hour] < params.diesel_min_kw:
            target_power = 0.0
        else:
            hour_lower = max(hour_lower, params.diesel_min_kw)
            target_power = float(np.clip(raw_diesel_kw[hour], hour_lower, hour_upper))

        if hour > 0 and diesel_kw[hour - 1] >= params.diesel_min_kw and target_power >= params.diesel_min_kw:
            ramp_lower = diesel_kw[hour - 1] - params.diesel_ramp_down_limit_kw
            ramp_upper = diesel_kw[hour - 1] + params.diesel_ramp_up_limit_kw
            continuous_lower = max(hour_lower, params.diesel_min_kw, ramp_lower)
            continuous_upper = min(hour_upper, ramp_upper)
            if continuous_lower <= continuous_upper:
                target_power = float(np.clip(target_power, continuous_lower, continuous_upper))
            else:
                target_power = float(np.clip(target_power, hour_lower, hour_upper))

        diesel_kw[hour] = target_power
    return diesel_kw


def battery_energy_profile(battery_kw, params=PARAMS):
    energy = np.empty(HOURS + 1, dtype=float)
    energy[0] = params.soc_initial * params.battery_capacity_kwh
    for hour, power in enumerate(battery_kw):
        energy[hour + 1] = (
            energy[hour] - power * params.time_step_hours / params.discharge_efficiency
            if power >= 0
            else energy[hour] + -power * params.time_step_hours * params.charge_efficiency
        )
    return energy


def diesel_ramp_violations(diesel_kw, params=PARAMS):
    consecutive_running = (diesel_kw[1:] >= params.diesel_min_kw) & (diesel_kw[:-1] >= params.diesel_min_kw)
    diesel_delta_kw = np.diff(diesel_kw)
    ramp_up_violation = np.where(
        consecutive_running,
        np.maximum(diesel_delta_kw - params.diesel_ramp_up_limit_kw, 0.0),
        0.0,
    )
    ramp_down_violation = np.where(
        consecutive_running,
        np.maximum(-diesel_delta_kw - params.diesel_ramp_down_limit_kw, 0.0),
        0.0,
    )
    return ramp_up_violation, ramp_down_violation


def battery_ramp_violations(battery_kw, params=PARAMS):
    if not params.enable_extra_complexity:
        return np.zeros(max(0, len(battery_kw) - 1), dtype=float)
    return np.maximum(np.abs(np.diff(battery_kw)) - params.battery_ramp_limit_kw, 0.0)


def grid_exchange_ramp_violations(grid_kw, params=PARAMS):
    if not params.enable_extra_complexity:
        return np.zeros(max(0, len(grid_kw) - 1), dtype=float)
    return np.maximum(np.abs(np.diff(grid_kw)) - params.grid_exchange_ramp_limit_kw, 0.0)


def battery_charge_state(charge_kw, eps=1.0e-8):
    return (np.asarray(charge_kw, dtype=float) > eps).astype(float)


def battery_mutual_exclusion_violations(charge_kw, discharge_kw, charge_state, params=PARAMS):
    charge_kw = np.asarray(charge_kw, dtype=float)
    discharge_kw = np.asarray(discharge_kw, dtype=float)
    charge_state = np.asarray(charge_state, dtype=float)
    charge_bound_violation = np.maximum(charge_kw - charge_state * params.battery_charge_max_kw, 0.0)
    discharge_bound_violation = np.maximum(
        discharge_kw - (1.0 - charge_state) * params.battery_discharge_max_kw,
        0.0,
    )
    simultaneous_violation = np.minimum(charge_kw, discharge_kw)
    return charge_bound_violation, discharge_bound_violation, simultaneous_violation


def evaluate_dispatch(solution, params=PARAMS):
    raw_diesel_kw, raw_battery_kw, raw_wind_curtail_fraction, raw_pv_curtail_fraction = split_solution(solution)
    wind_curtail_kw, pv_curtail_kw, actual_wind_kw, actual_pv_kw = repair_curtailment(
        raw_wind_curtail_fraction,
        raw_pv_curtail_fraction,
        params,
    )
    battery_kw = repair_battery_power(raw_battery_kw, actual_wind_kw, actual_pv_kw, params)
    diesel_kw = repair_diesel_power(raw_diesel_kw, battery_kw, actual_wind_kw, actual_pv_kw, params)
    grid_kw = LOAD_KW - actual_pv_kw - actual_wind_kw - diesel_kw - battery_kw
    energy_kwh = battery_energy_profile(battery_kw, params)
    soc = energy_kwh / params.battery_capacity_kwh

    buy_kw = np.maximum(grid_kw, 0.0)
    sell_kw = np.maximum(-grid_kw, 0.0)
    charge_kw = np.maximum(-battery_kw, 0.0)
    discharge_kw = np.maximum(battery_kw, 0.0)
    charge_state = battery_charge_state(charge_kw)
    (
        charge_bound_violation,
        discharge_bound_violation,
        simultaneous_violation,
    ) = battery_mutual_exclusion_violations(charge_kw, discharge_kw, charge_state, params)
    renewable_surplus_kw = renewable_surplus_profile(actual_wind_kw, actual_pv_kw)
    diesel_power_square_kwh = np.sum(diesel_kw ** 2) * params.time_step_hours

    economic_diesel_unit = max(params.diesel_unit_cost, float(np.mean(BUY_PRICE)) * 1.35)
    economic_cost = float(
        economic_diesel_unit * np.sum(diesel_kw) * params.time_step_hours
        + (
            params.diesel_quadratic_fuel_cost * diesel_power_square_kwh
            if params.enable_extra_complexity
            else 0.0
        )
        + params.battery_om_cost * np.sum(np.abs(battery_kw)) * params.time_step_hours
        + np.sum(BUY_PRICE * buy_kw - SELL_PRICE * sell_kw) * params.time_step_hours
    )

    diesel_kwh = np.sum(diesel_kw) * params.time_step_hours
    grid_buy_kwh = np.sum(buy_kw) * params.time_step_hours
    grid_exchange_kwh = np.sum(buy_kw + sell_kw) * params.time_step_hours
    curtailment_kwh = np.sum(wind_curtail_kw + pv_curtail_kw) * params.time_step_hours
    diesel_environment_unit = float(np.dot(DIESEL_EMISSION_FACTOR, TREATMENT_COST))
    raw_grid_environment_unit = float(np.dot(GRID_EMISSION_FACTOR, TREATMENT_COST))
    grid_environment_unit = max(raw_grid_environment_unit * 4.0, diesel_environment_unit * 2.8)
    curtailment_environment_unit = max(grid_environment_unit, diesel_environment_unit) * 1.2
    environment_cost = float(
        diesel_environment_unit * diesel_kwh
        + (
            params.diesel_quadratic_emission_cost * diesel_power_square_kwh
            if params.enable_extra_complexity
            else 0.0
        )
        + grid_environment_unit * grid_buy_kwh
        + curtailment_environment_unit * curtailment_kwh
        + 0.01 * grid_exchange_kwh
    )

    penalty = 0.0
    penalty += np.sum(np.maximum(buy_kw - params.grid_buy_max_kw, 0.0) ** 2)
    penalty += np.sum(np.maximum(sell_kw - params.grid_sell_max_kw, 0.0) ** 2)
    penalty += np.sum(np.maximum(sell_kw + charge_kw - renewable_surplus_kw, 0.0) ** 2)
    ramp_up_violation, ramp_down_violation = diesel_ramp_violations(diesel_kw, params)
    battery_ramp_violation = battery_ramp_violations(battery_kw, params)
    grid_exchange_ramp_violation = grid_exchange_ramp_violations(grid_kw, params)
    penalty += np.sum(ramp_up_violation ** 2)
    penalty += np.sum(ramp_down_violation ** 2)
    penalty += np.sum(battery_ramp_violation ** 2)
    penalty += np.sum(grid_exchange_ramp_violation ** 2)
    penalty += np.sum(charge_bound_violation ** 2)
    penalty += np.sum(discharge_bound_violation ** 2)
    penalty += np.sum(simultaneous_violation ** 2)
    penalty += (soc[-1] - params.soc_initial) ** 2
    penalty_value = float(params.penalty_weight * penalty)

    return {
        "diesel_kw": diesel_kw,
        "battery_kw": battery_kw,
        "battery_charge_kw": charge_kw,
        "battery_discharge_kw": discharge_kw,
        "battery_charge_state": charge_state,
        "grid_kw": grid_kw,
        "wind_available_kw": WT_KW.copy(),
        "pv_available_kw": PV_KW.copy(),
        "wind_curtail_kw": wind_curtail_kw,
        "pv_curtail_kw": pv_curtail_kw,
        "total_curtail_kw": wind_curtail_kw + pv_curtail_kw,
        "diesel_ramp_up_violation_kw": np.concatenate([[0.0], ramp_up_violation]),
        "diesel_ramp_down_violation_kw": np.concatenate([[0.0], ramp_down_violation]),
        "battery_ramp_violation_kw": np.concatenate([[0.0], battery_ramp_violation]),
        "grid_exchange_ramp_violation_kw": np.concatenate([[0.0], grid_exchange_ramp_violation]),
        "battery_charge_bound_violation_kw": charge_bound_violation,
        "battery_discharge_bound_violation_kw": discharge_bound_violation,
        "battery_mutual_exclusion_violation_kw": simultaneous_violation,
        "time_step_hours": params.time_step_hours,
        "time_h": (np.arange(HOURS, dtype=float) + 1.0) * params.time_step_hours,
        "renewable_surplus_kw": renewable_surplus_kw,
        "pv_kw": actual_pv_kw,
        "wt_kw": actual_wind_kw,
        "load_kw": LOAD_KW.copy(),
        "energy_kwh": energy_kwh,
        "soc": soc,
        "economic_cost": economic_cost,
        "environment_cost": environment_cost,
        "penalty": penalty_value,
    }


def objective_function(solution):
    dispatch = evaluate_dispatch(solution)
    penalty = dispatch["penalty"]
    return np.array(
        [
            dispatch["economic_cost"] + penalty,
            dispatch["environment_cost"] + penalty,
        ],
        dtype=float,
    )
