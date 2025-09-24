package com.example.agent.providers;

import com.example.tests.generator.config.GigachatClientConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GigachatTokenProviderTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    @Test
    void usesBasicAuthorizationHeaderWhenFetchingToken() throws Exception {
        GigachatClientConfig config = GigachatClientConfig.builder()
                .baseUrl(URI.create("https://api.example"))
                .authUrl(URI.create("https://auth.example/token"))
                .clientId("client-id")
                .clientSecret("client-secret")
                .build();

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"access_token\":\"token\",\"expires_in\":3600}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        GigachatTokenProvider provider = new GigachatTokenProvider(httpClient, config);

        provider.getAccessToken();

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = captor.getValue();

        String expectedHeader = "Basic " + Base64.getEncoder()
                .encodeToString("client-id:client-secret".getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedHeader, request.headers().firstValue("Authorization").orElseThrow());
    }
}
