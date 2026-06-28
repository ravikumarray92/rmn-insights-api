package rmn.insights.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Profile("!local")
@Configuration
public class SnowflakeDataSourceConfig {

    @Bean
    @Qualifier("snowflakeDataSource")
    public DataSource snowflakeDataSource(AppProperties props) {
        AppProperties.Snowflake sf = props.snowflake();
        String url = String.format(
                "jdbc:snowflake://%s.snowflakecomputing.com/?warehouse=%s&db=%s&schema=%s",
                sf.account(), sf.warehouse(), sf.database(), sf.schema()
        );
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("net.snowflake.client.jdbc.SnowflakeDriver");
        ds.setUrl(url);
        ds.setUsername(sf.user());
        ds.setPassword(sf.password());
        return ds;
    }

    @Bean
    @Qualifier("snowflakeJdbc")
    public NamedParameterJdbcTemplate snowflakeJdbcTemplate(
            @Qualifier("snowflakeDataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
