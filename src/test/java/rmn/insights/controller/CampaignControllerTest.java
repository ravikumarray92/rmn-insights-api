package rmn.insights.controller;

import rmn.insights.config.AppProperties;
import rmn.insights.config.SecurityConfig;
import rmn.insights.dto.ClickToBasketResponse;
import rmn.insights.dto.ClicksResponse;
import rmn.insights.dto.ImpressionsResponse;
import rmn.insights.dto.TimeRangeDto;
import rmn.insights.security.JwtAuthenticationFilter;
import rmn.insights.service.CampaignService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CampaignController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class CampaignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CampaignService campaignService;

    // JwtAuthenticationFilter needs AppProperties — provide a mock bean
    @MockBean
    private AppProperties appProperties;

    private static final String TENANT = "tenant-walmart-001";
    private static final String CAMPAIGN = "camp-abc-123";

    private ClicksResponse stubClicks() {
        TimeRangeDto window = new TimeRangeDto(
                Instant.parse("2026-06-27T00:00:00Z"),
                Instant.parse("2026-06-27T12:00:00Z")
        );
        return new ClicksResponse(CAMPAIGN, TENANT, 1500L, 900L, null, window, 60);
    }

    private ImpressionsResponse stubImpressions() {
        TimeRangeDto window = new TimeRangeDto(
                Instant.parse("2026-06-27T00:00:00Z"),
                Instant.parse("2026-06-27T12:00:00Z")
        );
        return new ImpressionsResponse(CAMPAIGN, TENANT, 45000L, 30000L, null, window, 60);
    }

    private ClickToBasketResponse stubCtb() {
        TimeRangeDto window = new TimeRangeDto(
                Instant.parse("2026-06-27T00:00:00Z"),
                Instant.parse("2026-06-27T12:00:00Z")
        );
        return new ClickToBasketResponse(CAMPAIGN, TENANT, 300L, 0.2, 30, window, 60);
    }

    // ---- Happy-path tests ----

    @Test
    @WithMockUser(username = TENANT)
    void getClicks_returns200() throws Exception {
        when(campaignService.getClicks(eq(TENANT), eq(CAMPAIGN), isNull(), eq(false)))
                .thenReturn(stubClicks());

        mockMvc.perform(MockMvcRequestBuilders.get("/v1/ad/{campaignId}/clicks", CAMPAIGN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(CAMPAIGN))
                .andExpect(jsonPath("$.tenantId").value(TENANT))
                .andExpect(jsonPath("$.totalClicks").value(1500))
                .andExpect(jsonPath("$.uniqueUsers").value(900))
                .andExpect(jsonPath("$.dataFreshnessSeconds").value(60));
    }

    @Test
    @WithMockUser(username = TENANT)
    void getImpressions_returns200() throws Exception {
        when(campaignService.getImpressions(eq(TENANT), eq(CAMPAIGN), isNull(), eq(false)))
                .thenReturn(stubImpressions());

        mockMvc.perform(MockMvcRequestBuilders.get("/v1/ad/{campaignId}/impressions", CAMPAIGN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(CAMPAIGN))
                .andExpect(jsonPath("$.totalImpressions").value(45000))
                .andExpect(jsonPath("$.uniqueReach").value(30000));
    }

    @Test
    @WithMockUser(username = TENANT)
    void getClickToBasket_returns200() throws Exception {
        when(campaignService.getClickToBasket(eq(TENANT), eq(CAMPAIGN), isNull()))
                .thenReturn(stubCtb());

        mockMvc.perform(MockMvcRequestBuilders.get("/v1/ad/{campaignId}/clickToBasket", CAMPAIGN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(CAMPAIGN))
                .andExpect(jsonPath("$.clickToBasketCount").value(300))
                .andExpect(jsonPath("$.clickToBasketRate").value(0.2))
                .andExpect(jsonPath("$.attributionWindowMinutes").value(30));
    }

    @Test
    @WithMockUser(username = TENANT)
    void getClicks_withTimeSeries_returns200() throws Exception {
        TimeRangeDto window = new TimeRangeDto(
                Instant.parse("2026-06-27T00:00:00Z"),
                Instant.parse("2026-06-27T12:00:00Z")
        );
        ClicksResponse withSeries = new ClicksResponse(
                CAMPAIGN, TENANT, 1500L, 900L,
                List.of(),
                window, 60
        );
        when(campaignService.getClicks(eq(TENANT), eq(CAMPAIGN), isNull(), eq(true)))
                .thenReturn(withSeries);

        mockMvc.perform(MockMvcRequestBuilders.get("/v1/ad/{campaignId}/clicks", CAMPAIGN)
                        .param("timeSeries", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeSeries").isArray());
    }

    // ---- Authentication / authorisation tests ----

    @Test
    void getClicks_noAuth_returns401() throws Exception {
        // No @WithMockUser — SecurityContext is empty, filter returns 401
        AppProperties.Jwt jwtProps = new AppProperties.Jwt("changeme");
        when(appProperties.jwt()).thenReturn(jwtProps);

        mockMvc.perform(MockMvcRequestBuilders.get("/v1/ad/{campaignId}/clicks", CAMPAIGN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getImpressions_noAuth_returns401() throws Exception {
        AppProperties.Jwt jwtProps = new AppProperties.Jwt("changeme");
        when(appProperties.jwt()).thenReturn(jwtProps);

        mockMvc.perform(MockMvcRequestBuilders.get("/v1/ad/{campaignId}/impressions", CAMPAIGN))
                .andExpect(status().isUnauthorized());
    }

    // ---- Validation tests ----

    @Test
    @WithMockUser(username = TENANT)
    void getClicks_startWithoutEnd_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/ad/{campaignId}/clicks", CAMPAIGN)
                        .param("start", "2026-06-26T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TENANT)
    void getClicks_endWithoutStart_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/ad/{campaignId}/clicks", CAMPAIGN)
                        .param("end", "2026-06-27T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TENANT)
    void getClicks_endBeforeStart_returns400() throws Exception {
        // CampaignService is NOT called; parseTimeRange throws before delegate
        when(campaignService.getClicks(anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(stubClicks());

        mockMvc.perform(MockMvcRequestBuilders.get("/v1/ad/{campaignId}/clicks", CAMPAIGN)
                        .param("start", "2026-06-27T12:00:00Z")
                        .param("end", "2026-06-27T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TENANT)
    void getImpressions_endBeforeStart_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/ad/{campaignId}/impressions", CAMPAIGN)
                        .param("start", "2026-06-27T10:00:00Z")
                        .param("end", "2026-06-27T08:00:00Z"))
                .andExpect(status().isBadRequest());
    }
}
