package rmn.insights.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClickToBasketResponse(
        String campaignId,
        String tenantId,
        long clickToBasketCount,
        double clickToBasketRate,
        int attributionWindowMinutes,
        TimeRangeDto queryWindow,
        int dataFreshnessSeconds
) {}
