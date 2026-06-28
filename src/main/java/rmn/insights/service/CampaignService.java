package rmn.insights.service;

import rmn.insights.config.AppProperties;
import rmn.insights.dto.AggregationResult;
import rmn.insights.dto.ClickToBasketResponse;
import rmn.insights.dto.ClicksResponse;
import rmn.insights.dto.ImpressionsResponse;
import rmn.insights.dto.MetricType;
import rmn.insights.dto.TimeRange;
import rmn.insights.dto.TimeRangeDto;
import rmn.insights.repository.ICampaignRepository;
import rmn.insights.repository.IHistoricalRepository;
import rmn.insights.repository.IRealtimeRepository;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Log4j2
@Service
public class CampaignService {

    private final IRealtimeRepository realtimeRepo;
    private final IHistoricalRepository historicalRepo;
    private final ICampaignRepository campaignRepo;
    private final AppProperties props;

    public CampaignService(IRealtimeRepository realtimeRepo,
                           IHistoricalRepository historicalRepo,
                           ICampaignRepository campaignRepo,
                           AppProperties props) {
        this.realtimeRepo = realtimeRepo;
        this.historicalRepo = historicalRepo;
        this.campaignRepo = campaignRepo;
        this.props = props;
    }

    private void validateOwnership(String tenantId, String campaignId) {
        if (!campaignRepo.existsByTenantAndCampaign(tenantId, campaignId)) {
            log.warn("Ownership check failed campaign={} tenant={}", campaignId, tenantId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Campaign not found or does not belong to tenant");
        }
    }

    private boolean useHistorical(TimeRange range) {
        return range.durationHours() > props.realtimeWindowHours();
    }

    private int dataFreshnessSeconds(boolean historical) {
        return historical ? 3600 : 60;
    }

    private TimeRange defaultTimeRange() {
        Instant now = Instant.now();
        Instant startOfDay = now.truncatedTo(ChronoUnit.DAYS);
        return new TimeRange(startOfDay, now);
    }

    @Observed(name = "campaign.clicks")
    public ClicksResponse getClicks(String tenantId, String campaignId,
                                    @Nullable TimeRange timeRange, boolean includeTimeSeries) {
        validateOwnership(tenantId, campaignId);
        TimeRange window = timeRange != null ? timeRange : defaultTimeRange();
        boolean hist = useHistorical(window);
        log.debug("clicks campaign={} tenant={} windowHours={} historical={}", campaignId, tenantId, window.durationHours(), hist);
        AggregationResult result = hist
                ? historicalRepo.query(tenantId, campaignId, MetricType.CLICKS, window)
                : realtimeRepo.queryDruid(tenantId, campaignId, MetricType.CLICKS, window);
        long uniqueUsers = hist
                ? result.uniqueUsers()
                : realtimeRepo.getUniqueCount(tenantId, campaignId, "unique_clicks");

        return new ClicksResponse(
                campaignId, tenantId,
                result.total(), uniqueUsers,
                includeTimeSeries ? result.timeSeries() : null,
                new TimeRangeDto(window.start(), window.end()),
                dataFreshnessSeconds(hist)
        );
    }

    @Observed(name = "campaign.impressions")
    public ImpressionsResponse getImpressions(String tenantId, String campaignId,
                                              @Nullable TimeRange timeRange, boolean includeTimeSeries) {
        validateOwnership(tenantId, campaignId);
        TimeRange window = timeRange != null ? timeRange : defaultTimeRange();
        boolean hist = useHistorical(window);
        log.debug("impressions campaign={} tenant={} windowHours={} historical={}", campaignId, tenantId, window.durationHours(), hist);
        AggregationResult result = hist
                ? historicalRepo.query(tenantId, campaignId, MetricType.IMPRESSIONS, window)
                : realtimeRepo.queryDruid(tenantId, campaignId, MetricType.IMPRESSIONS, window);
        long uniqueReach = hist
                ? result.uniqueUsers()
                : realtimeRepo.getUniqueCount(tenantId, campaignId, "unique_impressions");

        return new ImpressionsResponse(
                campaignId, tenantId,
                result.total(), uniqueReach,
                includeTimeSeries ? result.timeSeries() : null,
                new TimeRangeDto(window.start(), window.end()),
                dataFreshnessSeconds(hist)
        );
    }

    @Observed(name = "campaign.click_to_basket")
    public ClickToBasketResponse getClickToBasket(String tenantId, String campaignId,
                                                   @Nullable TimeRange timeRange) {
        validateOwnership(tenantId, campaignId);
        TimeRange window = timeRange != null ? timeRange : defaultTimeRange();
        boolean hist = useHistorical(window);
        log.debug("clickToBasket campaign={} tenant={} windowHours={} historical={}", campaignId, tenantId, window.durationHours(), hist);
        AggregationResult ctbResult;
        AggregationResult clickResult;
        if (hist) {
            ctbResult = historicalRepo.query(tenantId, campaignId, MetricType.CLICK_TO_BASKET, window);
            clickResult = historicalRepo.query(tenantId, campaignId, MetricType.CLICKS, window);
        } else {
            ctbResult = realtimeRepo.queryDruid(tenantId, campaignId, MetricType.CLICK_TO_BASKET, window);
            clickResult = realtimeRepo.queryDruid(tenantId, campaignId, MetricType.CLICKS, window);
        }
        double rate = clickResult.total() > 0
                ? Math.round((double) ctbResult.total() / clickResult.total() * 10000.0) / 10000.0
                : 0.0;

        return new ClickToBasketResponse(
                campaignId, tenantId,
                ctbResult.total(), rate,
                props.attributionWindowMinutes(),
                new TimeRangeDto(window.start(), window.end()),
                dataFreshnessSeconds(hist)
        );
    }
}
