package com.mg.mgserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_param")
public class DeviceParam {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "param_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private DispatchTask task;

    @Column(name = "micro_turbine_min_kw", nullable = false)
    private double microTurbineMinKw = 20.0;

    @Column(name = "micro_turbine_max_kw", nullable = false)
    private double microTurbineMaxKw = 160.0;

    @Column(name = "micro_turbine_ramp_up_kw", nullable = false)
    private double microTurbineRampUpKw = 55.0;

    @Column(name = "micro_turbine_ramp_down_kw", nullable = false)
    private double microTurbineRampDownKw = 55.0;

    @Column(name = "micro_turbine_unit_cost", nullable = false)
    private double microTurbineUnitCost = 0.78;

    @Column(name = "battery_capacity_kwh", nullable = false)
    private double batteryCapacityKwh = 360.0;

    @Column(name = "battery_charge_max_kw", nullable = false)
    private double batteryChargeMaxKw = 90.0;

    @Column(name = "battery_discharge_max_kw", nullable = false)
    private double batteryDischargeMaxKw = 90.0;

    @Column(name = "battery_soc_min", nullable = false)
    private double batterySocMin = 0.20;

    @Column(name = "battery_soc_max", nullable = false)
    private double batterySocMax = 0.90;

    @Column(name = "battery_soc_initial", nullable = false)
    private double batterySocInitial = 0.50;

    @Column(name = "grid_buy_max_kw", nullable = false)
    private double gridBuyMaxKw = 240.0;

    @Column(name = "grid_sell_max_kw", nullable = false)
    private double gridSellMaxKw = 160.0;

    @Column(name = "penalty_cost", nullable = false)
    private double renewableCurtailmentCost = 0.10;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public DispatchTask getTask() {
        return task;
    }

    public void setTask(DispatchTask task) {
        this.task = task;
    }

    public double getMicroTurbineMinKw() {
        return microTurbineMinKw;
    }

    public void setMicroTurbineMinKw(double microTurbineMinKw) {
        this.microTurbineMinKw = microTurbineMinKw;
    }

    public double getMicroTurbineMaxKw() {
        return microTurbineMaxKw;
    }

    public void setMicroTurbineMaxKw(double microTurbineMaxKw) {
        this.microTurbineMaxKw = microTurbineMaxKw;
    }

    public double getMicroTurbineRampUpKw() {
        return microTurbineRampUpKw;
    }

    public void setMicroTurbineRampUpKw(double microTurbineRampUpKw) {
        this.microTurbineRampUpKw = microTurbineRampUpKw;
    }

    public double getMicroTurbineRampDownKw() {
        return microTurbineRampDownKw;
    }

    public void setMicroTurbineRampDownKw(double microTurbineRampDownKw) {
        this.microTurbineRampDownKw = microTurbineRampDownKw;
    }

    public double getMicroTurbineUnitCost() {
        return microTurbineUnitCost;
    }

    public void setMicroTurbineUnitCost(double microTurbineUnitCost) {
        this.microTurbineUnitCost = microTurbineUnitCost;
    }

    public double getBatteryCapacityKwh() {
        return batteryCapacityKwh;
    }

    public void setBatteryCapacityKwh(double batteryCapacityKwh) {
        this.batteryCapacityKwh = batteryCapacityKwh;
    }

    public double getBatteryChargeMaxKw() {
        return batteryChargeMaxKw;
    }

    public void setBatteryChargeMaxKw(double batteryChargeMaxKw) {
        this.batteryChargeMaxKw = batteryChargeMaxKw;
    }

    public double getBatteryDischargeMaxKw() {
        return batteryDischargeMaxKw;
    }

    public void setBatteryDischargeMaxKw(double batteryDischargeMaxKw) {
        this.batteryDischargeMaxKw = batteryDischargeMaxKw;
    }

    public double getBatterySocMin() {
        return batterySocMin;
    }

    public void setBatterySocMin(double batterySocMin) {
        this.batterySocMin = batterySocMin;
    }

    public double getBatterySocMax() {
        return batterySocMax;
    }

    public void setBatterySocMax(double batterySocMax) {
        this.batterySocMax = batterySocMax;
    }

    public double getBatterySocInitial() {
        return batterySocInitial;
    }

    public void setBatterySocInitial(double batterySocInitial) {
        this.batterySocInitial = batterySocInitial;
    }

    public double getGridBuyMaxKw() {
        return gridBuyMaxKw;
    }

    public void setGridBuyMaxKw(double gridBuyMaxKw) {
        this.gridBuyMaxKw = gridBuyMaxKw;
    }

    public double getGridSellMaxKw() {
        return gridSellMaxKw;
    }

    public void setGridSellMaxKw(double gridSellMaxKw) {
        this.gridSellMaxKw = gridSellMaxKw;
    }

    public double getRenewableCurtailmentCost() {
        return renewableCurtailmentCost;
    }

    public void setRenewableCurtailmentCost(double renewableCurtailmentCost) {
        this.renewableCurtailmentCost = renewableCurtailmentCost;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void touch() {
        this.createdAt = LocalDateTime.now();
    }
}
