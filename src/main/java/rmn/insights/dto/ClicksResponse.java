package rmn.insights.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClicksResponse(
        String campaignId,
        String tenantId,
        long totalClicks,
        long uniqueUsers,
        List<MetricPoint> timeSeries,
        TimeRangeDto queryWindow,
        int dataFreshnessSeconds
) {}
