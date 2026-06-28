package rmn.insights.dto;

import java.time.Instant;

public record MetricPoint(Instant timestamp, long value) {}
