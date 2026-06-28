INSERT INTO campaigns (tenant_id, campaign_id, campaign_name) VALUES
    ('tenant-walmart-001', 'camp-spring-sale-2024', 'Spring Sale 2024'),
    ('tenant-walmart-001', 'camp-abc-123',           'Test Campaign ABC');

INSERT INTO campaign_metrics_hourly (tenant_id, campaign_id, metric_hour, clicks, impressions, click_to_basket, unique_users) VALUES
    ('tenant-walmart-001', 'camp-spring-sale-2024', DATEADD('HOUR', -5, NOW()), 120, 3500, 20, 100),
    ('tenant-walmart-001', 'camp-spring-sale-2024', DATEADD('HOUR', -4, NOW()), 150, 4200, 28, 130),
    ('tenant-walmart-001', 'camp-spring-sale-2024', DATEADD('HOUR', -3, NOW()), 180, 5100, 35, 160),
    ('tenant-walmart-001', 'camp-spring-sale-2024', DATEADD('HOUR', -2, NOW()), 200, 6000, 42, 185),
    ('tenant-walmart-001', 'camp-spring-sale-2024', DATEADD('HOUR', -1, NOW()), 170, 4800, 31, 150),
    ('tenant-walmart-001', 'camp-abc-123',           DATEADD('HOUR', -3, NOW()),  60, 1800, 10,  55),
    ('tenant-walmart-001', 'camp-abc-123',           DATEADD('HOUR', -2, NOW()),  80, 2200, 14,  72),
    ('tenant-walmart-001', 'camp-abc-123',           DATEADD('HOUR', -1, NOW()),  95, 2700, 18,  88);
