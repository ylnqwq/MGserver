package com.mg.mgserver.service;

import com.mg.mgserver.domain.DeviceParam;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DispatchCalculationService {
    public DispatchResult calculate(List<ProfilePoint> profiles, DeviceParam setting) {
        double averageBuyPrice = profiles.stream().mapToDouble(ProfilePoint::buyPrice).average().orElse(0.0);
        double energy = setting.getBatteryCapacityKwh() * setting.getBatterySocInitial();
        double minEnergy = setting.getBatteryCapacityKwh() * setting.getBatterySocMin();
        double maxEnergy = setting.getBatteryCapacityKwh() * setting.getBatterySocMax();
        double previousTurbine = 0.0;

        double economicCost = 0.0;
        double environmentCost = 0.0;
        double renewableAvailableKwh = 0.0;
        double curtailmentKwh = 0.0;
        List<DispatchCurvePoint> curves = new ArrayList<>();

        for (ProfilePoint point : profiles) {
            renewableAvailableKwh += point.pvKw() + point.wtKw();
            double netDemand = point.loadKw() - point.pvKw() - point.wtKw();
            double batteryKw = 0.0;
            double turbineKw = 0.0;
            double gridBuyKw = 0.0;
            double gridSellKw = 0.0;
            double curtailedKw = 0.0;

            if (netDemand >= 0) {
                if (point.buyPrice() >= averageBuyPrice) {
                    double availableDischarge = Math.max(0.0, energy - minEnergy);
                    batteryKw = Math.min(Math.min(setting.getBatteryDischargeMaxKw(), availableDischarge), netDemand * 0.45);
                    energy -= batteryKw;
                }
                double remaining = netDemand - batteryKw;
                if (remaining > setting.getGridBuyMaxKw() || point.buyPrice() >= averageBuyPrice) {
                    double target = point.buyPrice() >= averageBuyPrice ? remaining * 0.65 : remaining - setting.getGridBuyMaxKw();
                    turbineKw = boundedTurbine(target, previousTurbine, setting);
                    remaining -= turbineKw;
                }
                gridBuyKw = Math.min(setting.getGridBuyMaxKw(), Math.max(0.0, remaining));
                double shortage = Math.max(0.0, remaining - gridBuyKw);
                if (shortage > 0) {
                    turbineKw = boundedTurbine(turbineKw + shortage, previousTurbine, setting);
                    gridBuyKw = Math.min(setting.getGridBuyMaxKw(), Math.max(0.0, netDemand - batteryKw - turbineKw));
                }
            } else {
                double surplus = -netDemand;
                double availableCharge = Math.max(0.0, maxEnergy - energy);
                double chargeKw = Math.min(Math.min(setting.getBatteryChargeMaxKw(), availableCharge), surplus);
                batteryKw = -chargeKw;
                energy += chargeKw;
                surplus -= chargeKw;
                gridSellKw = Math.min(setting.getGridSellMaxKw(), surplus);
                curtailedKw = Math.max(0.0, surplus - gridSellKw);
            }

            previousTurbine = turbineKw;
            double soc = setting.getBatteryCapacityKwh() == 0 ? 0 : energy / setting.getBatteryCapacityKwh();
            economicCost += turbineKw * setting.getMicroTurbineUnitCost()
                    + gridBuyKw * point.buyPrice()
                    - gridSellKw * point.sellPrice()
                    + Math.abs(batteryKw) * 0.03
                    + curtailedKw * setting.getRenewableCurtailmentCost();
            environmentCost += turbineKw * 0.68 + gridBuyKw * 0.52;
            curtailmentKwh += curtailedKw;

            curves.add(new DispatchCurvePoint(
                    point.hour(),
                    round(point.buyPrice()),
                    round(point.sellPrice()),
                    round(point.loadKw()),
                    round(point.pvKw()),
                    round(point.wtKw()),
                    round(turbineKw),
                    round(batteryKw),
                    round(gridBuyKw),
                    round(gridSellKw),
                    round(soc),
                    round(curtailedKw)
            ));
        }

        double renewableUtilization = renewableAvailableKwh <= 0 ? 1.0 : 1.0 - curtailmentKwh / renewableAvailableKwh;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("algorithm", "RuleBasedDispatch-v1");
        summary.put("periods", profiles.size());
        summary.put("economicCost", round(economicCost));
        summary.put("environmentCost", round(environmentCost));
        summary.put("renewableUtilizationRate", round(renewableUtilization));
        summary.put("finalSoc", round(energy / setting.getBatteryCapacityKwh()));
        summary.put("totalCurtailmentKwh", round(curtailmentKwh));
        summary.put("microTurbineMaxKw", setting.getMicroTurbineMaxKw());
        summary.put("batteryCapacityKwh", setting.getBatteryCapacityKwh());
        return new DispatchResult(summary, curves);
    }

    private double boundedTurbine(double target, double previous, DeviceParam setting) {
        double rampLower = Math.max(0.0, previous - setting.getMicroTurbineRampDownKw());
        double rampUpper = Math.min(setting.getMicroTurbineMaxKw(), previous + setting.getMicroTurbineRampUpKw());
        double value = Math.max(rampLower, Math.min(rampUpper, target));
        if (value > 0 && value < setting.getMicroTurbineMinKw()) {
            value = Math.min(setting.getMicroTurbineMaxKw(), setting.getMicroTurbineMinKw());
        }
        return value;
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
