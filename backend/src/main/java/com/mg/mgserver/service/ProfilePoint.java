package com.mg.mgserver.service;

public record ProfilePoint(
        int hour,
        double buyPrice,
        double sellPrice,
        double loadKw,
        double pvKw,
        double wtKw
) {
}
