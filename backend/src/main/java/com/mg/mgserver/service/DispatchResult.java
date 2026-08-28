package com.mg.mgserver.service;

import java.util.List;
import java.util.Map;

public record DispatchResult(
        Map<String, Object> summary,
        List<DispatchCurvePoint> curves
) {
}
