CREATE TABLE IF NOT EXISTS campaigns (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id     VARCHAR(100) NOT NULL,
    campaign_id   VARCHAR(100) NOT NULL,
    campaign_name VARCHAR(200),
    CONSTRAINT uq_campaign UNIQUE (tenant_id, campaign_id)
);

CREATE TABLE IF NOT EXISTS campaign_metrics_hourly (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       VARCHAR(100) NOT NULL,
    campaign_id     VARCHAR(100) NOT NULL,
    metric_hour     TIMESTAMP    NOT NULL,
    clicks          BIGINT DEFAULT 0,
    impressions     BIGINT DEFAULT 0,
    click_to_basket BIGINT DEFAULT 0,
    unique_users    BIGINT DEFAULT 0
);
