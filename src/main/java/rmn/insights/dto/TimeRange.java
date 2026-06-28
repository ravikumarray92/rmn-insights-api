package rmn.insights.dto;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record TimeRange(Instant start, Instant end) {

    public TimeRange {
        Objects.requireNonNull(start, "start is required");
        Objects.requireNonNull(end, "end is required");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("'end' must be after 'start'");
        }
    }

    public double durationHours() {
        return Duration.between(start, end).toSeconds() / 3600.0;
    }
}
