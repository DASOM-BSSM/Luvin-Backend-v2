package com.luvin.ai.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * AI 서비스 전용 RestClient. 앱 전역 ObjectMapper와 분리해서 wire format(snake_case, UUID, ISO-8601)을
 * AI 서비스 계약에 맞게 독립적으로 관리한다 (요구사항 3.1).
 */
@Configuration
@EnableConfigurationProperties(AiServiceProperties.class)
@org.springframework.scheduling.annotation.EnableScheduling
public class AiClientConfig {

    @Bean
    public ObjectMapper aiObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    public RestClient aiRestClient(AiServiceProperties properties, ObjectMapper aiObjectMapper) {
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            throw new IllegalStateException(
                    "app.ai.base-url(AI_SERVICE_BASE_URL)이 설정되지 않았습니다.");
        }

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.of(properties.connectTimeout()))
                .setResponseTimeout(Timeout.of(properties.readTimeout()))
                .build();

        HttpClientBuilder httpClientBuilder = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig);

        ClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(0, new MappingJackson2HttpMessageConverter(aiObjectMapper));
                })
                .build();
    }
}
