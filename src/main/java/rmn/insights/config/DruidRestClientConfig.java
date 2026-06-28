package rmn.insights.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Profile("!local")
@Configuration
public class DruidRestClientConfig {

    @Bean
    public RestClient druidRestClient(AppProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.druid().queryTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        return RestClient.builder()
                .baseUrl(props.druid().brokerUrl())
                .requestFactory(factory)
                .build();
    }
}
