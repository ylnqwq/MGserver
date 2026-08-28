package com.mg.mgserver.dto;

import com.mg.mgserver.domain.AlgoConfig;
import com.mg.mgserver.domain.DeviceParam;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;

public final class SettingDtos {
    private SettingDtos() {
    }

    public record SettingRequest(
            @PositiveOrZero double microTurbineMinKw,
            @Positive double microTurbineMaxKw,
            @PositiveOrZero double microTurbineRampUpKw,
            @PositiveOrZero double microTurbineRampDownKw,
            @PositiveOrZero double microTurbineUnitCost,
            @Positive double batteryCapacityKwh,
            @PositiveOrZero double batteryChargeMaxKw,
            @PositiveOrZero double batteryDischargeMaxKw,
            @DecimalMin("0.0") @DecimalMax("1.0") double batterySocMin,
            @DecimalMin("0.0") @DecimalMax("1.0") double batterySocMax,
            @DecimalMin("0.0") @DecimalMax("1.0") double batterySocInitial,
            @PositiveOrZero double gridBuyMaxKw,
            @PositiveOrZero double gridSellMaxKw,
            @PositiveOrZero double renewableCurtailmentCost
    ) {
    }

    public record SettingResponse(
            double microTurbineMinKw,
            double microTurbineMaxKw,
            double microTurbineRampUpKw,
            double microTurbineRampDownKw,
            double microTurbineUnitCost,
            double batteryCapacityKwh,
            double batteryChargeMaxKw,
            double batteryDischargeMaxKw,
            double batterySocMin,
            double batterySocMax,
            double batterySocInitial,
            double gridBuyMaxKw,
            double gridSellMaxKw,
            double renewableCurtailmentCost,
            LocalDateTime updatedAt
    ) {
        public static SettingResponse from(DeviceParam setting) {
            return new SettingResponse(
                    setting.getMicroTurbineMinKw(),
                    setting.getMicroTurbineMaxKw(),
                    setting.getMicroTurbineRampUpKw(),
                    setting.getMicroTurbineRampDownKw(),
                    setting.getMicroTurbineUnitCost(),
                    setting.getBatteryCapacityKwh(),
                    setting.getBatteryChargeMaxKw(),
                    setting.getBatteryDischargeMaxKw(),
                    setting.getBatterySocMin(),
                    setting.getBatterySocMax(),
                    setting.getBatterySocInitial(),
                    setting.getGridBuyMaxKw(),
                    setting.getGridSellMaxKw(),
                    setting.getRenewableCurtailmentCost(),
                    setting.getCreatedAt()
            );
        }
    }

    public record AlgorithmSettingRequest(
            @Positive int bee,
            @Positive int maxIter,
            @Positive int limit,
            @Positive int archiveSize,
            @Positive int tournamentSize,
            @DecimalMin("0.0") @DecimalMax("1.0") double eliteRate,
            @DecimalMin("0.0") @DecimalMax("1.0") double eliminationRate,
            @DecimalMin("0.0") @DecimalMax("1.0") double archiveGuidanceRate
    ) {
    }

    public record AlgorithmSettingResponse(
            int bee,
            int maxIter,
            int limit,
            int archiveSize,
            int tournamentSize,
            double eliteRate,
            double eliminationRate,
            double archiveGuidanceRate,
            LocalDateTime updatedAt
    ) {
        public static AlgorithmSettingResponse from(AlgoConfig setting) {
            return new AlgorithmSettingResponse(
                    setting.getBeeCount(),
                    setting.getMaxIter(),
                    setting.getLimit(),
                    setting.getArchiveSize(),
                    setting.getTournamentSize(),
                    setting.getEliteRate(),
                    setting.getEliminationRate(),
                    setting.getArchiveGuidanceRate(),
                    setting.getUpdatedAt()
            );
        }
    }
}
