package rmn.insights.service;

import rmn.insights.config.AppProperties;
import rmn.insights.dto.AggregationResult;
import rmn.insights.dto.ClickToBasketResponse;
import rmn.insights.dto.ClicksResponse;
import rmn.insights.dto.ImpressionsResponse;
import rmn.insights.dto.MetricType;
import rmn.insights.dto.TimeRange;
import rmn.insights.repository.ICampaignRepository;
import rmn.insights.repository.IHistoricalRepository;
import rmn.insights.repository.IRealtimeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    @Mock
    private IRealtimeRepository realtimeRepo;

    @Mock
    private IHistoricalRepository historicalRepo;

    @Mock
    private ICampaignRepository campaignRepo;

    @Mock
    private AppProperties props;

    @InjectMocks
    private CampaignService campaignService;

    private static final String TENANT = "tenant-walmart-001";
    private static final String CAMPAIGN = "camp-abc-123";

    private static final AggregationResult EMPTY_RESULT =
            new AggregationResult(0L, 0L, List.of());

    private static final AggregationResult SAMPLE_RESULT =
            new AggregationResult(1500L, 900L, List.of());

    @BeforeEach
    void configureMocks() {
        when(props.realtimeWindowHours()).thenReturn(72);
        when(campaignRepo.existsByTenantAndCampaign(TENANT, CAMPAIGN)).thenReturn(true);
    }

    // ---- 24-hour window → realtime (Druid) ----

    @Test
    void getClicks_24hWindow_callsRealtimeRepo() {
        Instant now = Instant.now();
        TimeRange range = new TimeRange(now.minusSeconds(24 * 3600), now);

        when(realtimeRepo.queryDruid(TENANT, CAMPAIGN, MetricType.CLICKS, range))
                .thenReturn(SAMPLE_RESULT);
        when(realtimeRepo.getUniqueCount(TENANT, CAMPAIGN, "unique_clicks"))
                .thenReturn(900L);

        ClicksResponse response = campaignService.getClicks(TENANT, CAMPAIGN, range, false);

        verify(realtimeRepo).queryDruid(TENANT, CAMPAIGN, MetricType.CLICKS, range);
        verify(historicalRepo, never()).query(any(), any(), any(), any());
        assertThat(response.totalClicks()).isEqualTo(1500L);
        assertThat(response.dataFreshnessSeconds()).isEqualTo(60);
    }

    // ---- 200-hour window → historical (Snowflake) ----

    @Test
    void getClicks_200hWindow_callsHistoricalRepo() {
        Instant now = Instant.now();
        TimeRange range = new TimeRange(now.minusSeconds(200 * 3600), now);

        when(historicalRepo.query(TENANT, CAMPAIGN, MetricType.CLICKS, range))
                .thenReturn(SAMPLE_RESULT);

        ClicksResponse response = campaignService.getClicks(TENANT, CAMPAIGN, range, false);

        verify(historicalRepo).query(TENANT, CAMPAIGN, MetricType.CLICKS, range);
        verify(realtimeRepo, never()).queryDruid(any(), any(), any(), any());
        assertThat(response.totalClicks()).isEqualTo(1500L);
        assertThat(response.dataFreshnessSeconds()).isEqualTo(3600);
    }

    // ---- Exactly 72h → realtime (boundary is exclusive on historical side) ----

    @Test
    void getClicks_exactly72hWindow_callsRealtimeRepo() {
        Instant now = Instant.now();
        TimeRange range = new TimeRange(now.minusSeconds(72 * 3600), now);

        when(realtimeRepo.queryDruid(TENANT, CAMPAIGN, MetricType.CLICKS, range))
                .thenReturn(SAMPLE_RESULT);
        when(realtimeRepo.getUniqueCount(TENANT, CAMPAIGN, "unique_clicks"))
                .thenReturn(900L);

        ClicksResponse response = campaignService.getClicks(TENANT, CAMPAIGN, range, false);

        verify(realtimeRepo).queryDruid(TENANT, CAMPAIGN, MetricType.CLICKS, range);
        verify(historicalRepo, never()).query(any(), any(), any(), any());
        assertThat(response.dataFreshnessSeconds()).isEqualTo(60);
    }

    // ---- Impressions routing ----

    @Test
    void getImpressions_24hWindow_callsRealtimeRepo() {
        Instant now = Instant.now();
        TimeRange range = new TimeRange(now.minusSeconds(24 * 3600), now);

        when(realtimeRepo.queryDruid(TENANT, CAMPAIGN, MetricType.IMPRESSIONS, range))
                .thenReturn(SAMPLE_RESULT);
        when(realtimeRepo.getUniqueCount(TENANT, CAMPAIGN, "unique_impressions"))
                .thenReturn(30000L);

        ImpressionsResponse response = campaignService.getImpressions(TENANT, CAMPAIGN, range, false);

        verify(realtimeRepo).queryDruid(TENANT, CAMPAIGN, MetricType.IMPRESSIONS, range);
        verify(historicalRepo, never()).query(any(), any(), any(), any());
        assertThat(response.totalImpressions()).isEqualTo(1500L);
        assertThat(response.dataFreshnessSeconds()).isEqualTo(60);
    }

    @Test
    void getImpressions_200hWindow_callsHistoricalRepo() {
        Instant now = Instant.now();
        TimeRange range = new TimeRange(now.minusSeconds(200 * 3600), now);

        when(historicalRepo.query(TENANT, CAMPAIGN, MetricType.IMPRESSIONS, range))
                .thenReturn(SAMPLE_RESULT);

        ImpressionsResponse response = campaignService.getImpressions(TENANT, CAMPAIGN, range, false);

        verify(historicalRepo).query(TENANT, CAMPAIGN, MetricType.IMPRESSIONS, range);
        verify(realtimeRepo, never()).queryDruid(any(), any(), any(), any());
        assertThat(response.dataFreshnessSeconds()).isEqualTo(3600);
    }

    // ---- CTB rate = 0.0 when clicks = 0 ----

    @Test
    void getClickToBasket_zeroClicks_rateIsZero() {
        Instant now = Instant.now();
        TimeRange range = new TimeRange(now.minusSeconds(24 * 3600), now);

        when(props.attributionWindowMinutes()).thenReturn(30);
        AggregationResult ctbResult = new AggregationResult(50L, 0L, List.of());
        AggregationResult clickResult = new AggregationResult(0L, 0L, List.of());

        when(realtimeRepo.queryDruid(TENANT, CAMPAIGN, MetricType.CLICK_TO_BASKET, range))
                .thenReturn(ctbResult);
        when(realtimeRepo.queryDruid(TENANT, CAMPAIGN, MetricType.CLICKS, range))
                .thenReturn(clickResult);

        ClickToBasketResponse response = campaignService.getClickToBasket(TENANT, CAMPAIGN, range);

        assertThat(response.clickToBasketRate()).isEqualTo(0.0);
        assertThat(response.clickToBasketCount()).isEqualTo(50L);
    }

    // ---- CTB rate = 0.2 when ctb=200, clicks=1000 ----

    @Test
    void getClickToBasket_rate_isComputedCorrectly() {
        Instant now = Instant.now();
        TimeRange range = new TimeRange(now.minusSeconds(24 * 3600), now);

        when(props.attributionWindowMinutes()).thenReturn(30);
        AggregationResult ctbResult = new AggregationResult(200L, 0L, List.of());
        AggregationResult clickResult = new AggregationResult(1000L, 0L, List.of());

        when(realtimeRepo.queryDruid(TENANT, CAMPAIGN, MetricType.CLICK_TO_BASKET, range))
                .thenReturn(ctbResult);
        when(realtimeRepo.queryDruid(TENANT, CAMPAIGN, MetricType.CLICKS, range))
                .thenReturn(clickResult);

        ClickToBasketResponse response = campaignService.getClickToBasket(TENANT, CAMPAIGN, range);

        assertThat(response.clickToBasketRate()).isEqualTo(0.2);
    }

    // ---- dataFreshnessSeconds ----

    @Test
    void dataFreshness_isCorrectForRealtime() {
        Instant now = Instant.now();
        TimeRange range = new TimeRange(now.minusSeconds(24 * 3600), now);

        when(realtimeRepo.queryDruid(TENANT, CAMPAIGN, MetricType.CLICKS, range))
                .thenReturn(SAMPLE_RESULT);
        when(realtimeRepo.getUniqueCount(eq(TENANT), eq(CAMPAIGN), any()))
                .thenReturn(0L);

        ClicksResponse response = campaignService.getClicks(TENANT, CAMPAIGN, range, false);
        assertThat(response.dataFreshnessSeconds()).isEqualTo(60);
    }

    @Test
    void dataFreshness_isCorrectForHistorical() {
        Instant now = Instant.now();
        TimeRange range = new TimeRange(now.minusSeconds(200 * 3600), now);

        when(historicalRepo.query(TENANT, CAMPAIGN, MetricType.CLICKS, range))
                .thenReturn(SAMPLE_RESULT);

        ClicksResponse response = campaignService.getClicks(TENANT, CAMPAIGN, range, false);
        assertThat(response.dataFreshnessSeconds()).isEqualTo(3600);
    }

    // ---- timeSeries inclusion ----

    @Test
    void getClicks_includeTimeSeries_true_returnsTimeSeries() {
        Instant now = Instant.now();
        TimeRange range = new TimeRange(now.minusSeconds(24 * 3600), now);
        AggregationResult resultWithSeries = new AggregationResult(100L, 50L, List.of());

        when(realtimeRepo.queryDruid(TENANT, CAMPAIGN, MetricType.CLICKS, range))
                .thenReturn(resultWithSeries);
        when(realtimeRepo.getUniqueCount(any(), any(), any())).thenReturn(50L);

        ClicksResponse response = campaignService.getClicks(TENANT, CAMPAIGN, range, true);
        assertThat(response.timeSeries()).isNotNull();
    }

    @Test
    void getClicks_includeTimeSeries_false_returnsNullTimeSeries() {
        Instant now = Instant.now();
        TimeRange range = new TimeRange(now.minusSeconds(24 * 3600), now);

        when(realtimeRepo.queryDruid(TENANT, CAMPAIGN, MetricType.CLICKS, range))
                .thenReturn(SAMPLE_RESULT);
        when(realtimeRepo.getUniqueCount(any(), any(), any())).thenReturn(0L);

        ClicksResponse response = campaignService.getClicks(TENANT, CAMPAIGN, range, false);
        assertThat(response.timeSeries()).isNull();
    }

    // ---- Ownership check ----

    @Test
    void getClicks_campaignNotOwnedByTenant_throwsForbidden() {
        when(campaignRepo.existsByTenantAndCampaign(TENANT, CAMPAIGN)).thenReturn(false);
        Instant now = Instant.now();
        TimeRange range = new TimeRange(now.minusSeconds(24 * 3600), now);

        assertThatThrownBy(() -> campaignService.getClicks(TENANT, CAMPAIGN, range, false))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(403);

        verify(realtimeRepo, never()).queryDruid(any(), any(), any(), any());
        verify(historicalRepo, never()).query(any(), any(), any(), any());
    }

    @Test
    void getImpressions_campaignNotOwnedByTenant_throwsForbidden() {
        when(campaignRepo.existsByTenantAndCampaign(TENANT, CAMPAIGN)).thenReturn(false);
        Instant now = Instant.now();
        TimeRange range = new TimeRange(now.minusSeconds(24 * 3600), now);

        assertThatThrownBy(() -> campaignService.getImpressions(TENANT, CAMPAIGN, range, false))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(403);

        verify(realtimeRepo, never()).queryDruid(any(), any(), any(), any());
    }

    @Test
    void getClickToBasket_campaignNotOwnedByTenant_throwsForbidden() {
        when(campaignRepo.existsByTenantAndCampaign(TENANT, CAMPAIGN)).thenReturn(false);
        Instant now = Instant.now();
        TimeRange range = new TimeRange(now.minusSeconds(24 * 3600), now);

        assertThatThrownBy(() -> campaignService.getClickToBasket(TENANT, CAMPAIGN, range))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(403);

        verify(realtimeRepo, never()).queryDruid(any(), any(), any(), any());
    }
}
