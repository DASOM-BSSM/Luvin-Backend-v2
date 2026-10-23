package com.luvin.ai.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 장면 블록 회차 생성용 RestClient(긴 timeout)와 백그라운드 생성 executor. */
@Configuration
@EnableConfigurationProperties(AiStoryProperties.class)
public class AiStoryConfig {

    /**
     * 공통 데이터 계약(Spring ↔ FastAPI)은 camelCase다. 기존 AI 호출용 aiObjectMapper(snake_case)와 분리한다.
     * 계약에 없는 필드가 와도 실패하지 않게 하고, 빠진 필드는 Spring 검증(AiStoryResponseValidator)이 잡는다.
     * 빈으로 등록하지 않는다 — ObjectMapper를 타입으로 주입받는 곳(보안 핸들러 등)이 둘 중 하나를 고르지 못해 기동이 실패한다.
     */
    public static final ObjectMapper STORY_OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Bean
    public RestClient aiStoryRestClient(AiServiceProperties aiProperties, AiStoryProperties storyProperties) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.of(aiProperties.connectTimeout()))
                .setResponseTimeout(Timeout.of(storyProperties.readTimeout()))
                .build();
        return RestClient.builder()
                .baseUrl(aiProperties.baseUrl())
                .requestFactory(new HttpComponentsClientHttpRequestFactory(
                        HttpClients.custom().setDefaultRequestConfig(requestConfig).build()))
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(0, new MappingJackson2HttpMessageConverter(STORY_OBJECT_MAPPER));
                })
                .build();
    }

    /** GET content가 시작한 회차 생성을 요청 스레드 밖에서 끝낸다. 생성은 대부분 FastAPI 응답 대기라 가상 스레드를 쓴다. */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService aiStoryExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
