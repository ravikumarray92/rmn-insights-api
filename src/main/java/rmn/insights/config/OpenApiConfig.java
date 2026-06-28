package rmn.insights.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "RMN Insights API",
                version = "0.1.0",
                description = "Real-time campaign analytics API for the Retail Media Network. " +
                              "All endpoints require a signed JWT with a `tenant_id` claim in the Authorization header."
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT signed with HMAC-256. Payload must contain {\"tenant_id\": \"<id>\"}."
)
public class OpenApiConfig {}
