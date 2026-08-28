package com.mg.mgserver.service;

public record DispatchCurvePoint(
        int hour,
        double buyPrice,
        double sellPrice,
        double loadKw,
        double pvKw,
        double wtKw,
        double microTurbineKw,
        double batteryKw,
        double gridBuyKw,
        double gridSellKw,
        double soc,
        double curtailedKw
) {
}
