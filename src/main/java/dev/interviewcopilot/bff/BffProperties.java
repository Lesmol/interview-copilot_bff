package dev.interviewcopilot.bff;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bff")
public record BffProperties(String internalGatewayUrl, String tokenUri, String clientId,
                            String clientSecret, String scopes) {
}
