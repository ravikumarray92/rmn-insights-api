package rmn.insights.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rmn")
public record AppProperties(
        Druid druid,
        Snowflake snowflake,
        int realtimeWindowHours,
        int attributionWindowMinutes,
        Jwt jwt
) {
    public record Druid(String brokerUrl, int queryTimeoutMs) {}

    public record Snowflake(
            String account,
            String user,
            String password,
            String warehouse,
            String database,
            String schema
    ) {}

    public record Jwt(String secret) {}
}
