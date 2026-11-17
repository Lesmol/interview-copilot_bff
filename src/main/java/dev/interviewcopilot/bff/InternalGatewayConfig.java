package dev.interviewcopilot.bff;

import java.util.Arrays;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.web.client.RestClient;

/**
 * Outbound calls to the internal gateway carry a client-credentials service token
 * (cached by the authorized client service until near expiry) plus the caller's {@code X-User-Id}.
 */
@Configuration
@EnableConfigurationProperties(BffProperties.class)
class InternalGatewayConfig {

    static final String REGISTRATION_ID = "interview-copilot-bff";

    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(BffProperties props) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(props.clientId())
                .clientSecret(props.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(props.tokenUri())
                .scope(Arrays.asList(props.scopes().split("[ ,]+")))
                .build();
        var repository = new InMemoryClientRegistrationRepository(registration);
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                repository, new InMemoryOAuth2AuthorizedClientService(repository));
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials().build());
        return manager;
    }

    @Bean
    RestClient internalGatewayClient(BffProperties props, OAuth2AuthorizedClientManager manager) {
        return RestClient.builder()
                .baseUrl(props.internalGatewayUrl())
                .requestInterceptor((request, body, execution) -> {
                    var authorized = manager.authorize(OAuth2AuthorizeRequest
                            .withClientRegistrationId(REGISTRATION_ID)
                            .principal(REGISTRATION_ID).build());
                    if (authorized == null) {
                        throw new IllegalStateException("Could not obtain a service token");
                    }
                    request.getHeaders().set(HttpHeaders.AUTHORIZATION,
                            "Bearer " + authorized.getAccessToken().getTokenValue());
                    return execution.execute(request, body);
                })
                .build();
    }
}
