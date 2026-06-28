package rmn.insights.repository;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Log4j2
@Profile("!local")
@Repository
public class CampaignRepository implements ICampaignRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CampaignRepository(@Qualifier("snowflakeJdbc") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existsByTenantAndCampaign(String tenantId, String campaignId) {
        String sql = "SELECT COUNT(1) FROM campaigns WHERE tenant_id = :tenantId AND campaign_id = :campaignId";
        Map<String, Object> params = Map.of("tenantId", tenantId, "campaignId", campaignId);
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        boolean exists = count != null && count > 0;
        log.debug("ownership check campaign={} tenant={} exists={}", campaignId, tenantId, exists);
        return exists;
    }
}
