package rmn.insights.repository;

public interface ICampaignRepository {

    boolean existsByTenantAndCampaign(String tenantId, String campaignId);
}
