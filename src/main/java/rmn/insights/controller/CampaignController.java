package rmn.insights.controller;

import rmn.insights.dto.ClickToBasketResponse;
import rmn.insights.dto.ClicksResponse;
import rmn.insights.dto.ImpressionsResponse;
import rmn.insights.dto.TimeRange;
import rmn.insights.service.CampaignService;
import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.log4j.Log4j2;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Log4j2
@RestController
@RequestMapping("/v1/ad")
@Tag(name = "Campaign Insights", description = "Real-time and historical metrics for ad campaigns")
@SecurityRequirement(name = "bearerAuth")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    private String tenantId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private TimeRange parseTimeRange(Instant start, Instant end) {
        if (start == null && end == null) {
            return null;
        }
        if (start == null || end == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Both 'start' and 'end' must be provided together");
        }
        try {
            return new TimeRange(start, end);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/{campaignId}/clicks")
    @Observed(name = "api.clicks")
    @Operation(
            summary = "Get click metrics for a campaign",
            description = "Returns total clicks and unique users. Queries Druid for ≤72h windows, Snowflake for older data.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Click metrics returned"),
                    @ApiResponse(responseCode = "400", description = "Invalid time range parameters"),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
            }
    )
    public ClicksResponse getClicks(
            @Parameter(description = "Campaign identifier") @PathVariable String campaignId,
            @Parameter(description = "Window start (ISO 8601). Defaults to today 00:00 UTC if omitted.") @RequestParam(required = false) Instant start,
            @Parameter(description = "Window end (ISO 8601). Required when start is supplied.") @RequestParam(required = false) Instant end,
            @Parameter(description = "Include per-hour time series breakdown") @RequestParam(defaultValue = "false") boolean timeSeries) {
        log.debug("clicks request campaign={} start={} end={} timeSeries={}", campaignId, start, end, timeSeries);
        return campaignService.getClicks(tenantId(), campaignId, parseTimeRange(start, end), timeSeries);
    }

    @GetMapping("/{campaignId}/impressions")
    @Observed(name = "api.impressions")
    @Operation(
            summary = "Get impression metrics for a campaign",
            description = "Returns total impressions and unique reach. Queries Druid for ≤72h windows, Snowflake for older data.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Impression metrics returned"),
                    @ApiResponse(responseCode = "400", description = "Invalid time range parameters"),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
            }
    )
    public ImpressionsResponse getImpressions(
            @Parameter(description = "Campaign identifier") @PathVariable String campaignId,
            @Parameter(description = "Window start (ISO 8601). Defaults to today 00:00 UTC if omitted.") @RequestParam(required = false) Instant start,
            @Parameter(description = "Window end (ISO 8601). Required when start is supplied.") @RequestParam(required = false) Instant end,
            @Parameter(description = "Include per-hour time series breakdown") @RequestParam(defaultValue = "false") boolean timeSeries) {
        log.debug("impressions request campaign={} start={} end={} timeSeries={}", campaignId, start, end, timeSeries);
        return campaignService.getImpressions(tenantId(), campaignId, parseTimeRange(start, end), timeSeries);
    }

    @GetMapping("/{campaignId}/clickToBasket")
    @Observed(name = "api.clickToBasket")
    @Operation(
            summary = "Get click-to-basket metrics for a campaign",
            description = "Returns click-to-basket count and conversion rate within the configured attribution window.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Click-to-basket metrics returned"),
                    @ApiResponse(responseCode = "400", description = "Invalid time range parameters"),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
            }
    )
    public ClickToBasketResponse getClickToBasket(
            @Parameter(description = "Campaign identifier") @PathVariable String campaignId,
            @Parameter(description = "Window start (ISO 8601). Defaults to today 00:00 UTC if omitted.") @RequestParam(required = false) Instant start,
            @Parameter(description = "Window end (ISO 8601). Required when start is supplied.") @RequestParam(required = false) Instant end) {
        log.debug("clickToBasket request campaign={} start={} end={}", campaignId, start, end);
        return campaignService.getClickToBasket(tenantId(), campaignId, parseTimeRange(start, end));
    }
}
