package com.luvin.push.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luvin.push.config.PushProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Expo 푸시 서비스(https://docs.expo.dev/push-notifications/sending-notifications/)로 보낸다. 한 요청에 최대 100건이라 나눠
 * 보내고, 응답 ticket(요청 순서와 같은 순서)에서 DeviceNotRegistered인 토큰을 돌려준다.
 * receipt 조회(GET receipts)는 하지 않는다 — 전송 직후 ticket에서 바로 알 수 있는 DeviceNotRegistered만 정리한다.
 */
public class ExpoPushClient implements PushClient {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    static final int MAX_BATCH = 100;
    static final String DEVICE_NOT_REGISTERED = "DeviceNotRegistered";

    private final RestClient restClient;

    public ExpoPushClient(PushProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.expoUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (properties.accessToken() != null && !properties.accessToken().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken());
        }
        this.restClient = builder.build();
    }

    @Override
    public Set<String> send(List<PushMessage> messages) {
        Set<String> unregistered = new HashSet<>();
        for (int from = 0; from < messages.size(); from += MAX_BATCH) {
            List<PushMessage> batch = messages.subList(from, Math.min(from + MAX_BATCH, messages.size()));
            String response = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(write(batch))
                    .retrieve()
                    .body(String.class);
            unregistered.addAll(unregisteredTokens(batch, response));
        }
        return unregistered;
    }

    /** ticket은 보낸 순서대로 온다. status error + details.error DeviceNotRegistered면 그 토큰은 더 쓸 수 없다. */
    static Set<String> unregisteredTokens(List<PushMessage> batch, String response) {
        Set<String> unregistered = new HashSet<>();
        try {
            JsonNode tickets = JSON.readTree(response == null ? "{}" : response).path("data");
            for (int i = 0; i < tickets.size() && i < batch.size(); i++) {
                JsonNode ticket = tickets.get(i);
                if (!"error".equals(ticket.path("status").asText())) {
                    continue;
                }
                String error = ticket.path("details").path("error").asText("");
                if (DEVICE_NOT_REGISTERED.equals(error)) {
                    unregistered.add(batch.get(i).to());
                } else {
                    log.warn("expo push ticket error: error={}, message={}", error, ticket.path("message").asText(""));
                }
            }
        } catch (Exception e) {
            log.warn("expo push response unreadable: cause={}", e.getClass().getSimpleName());
        }
        return unregistered;
    }

    private static String write(List<PushMessage> batch) {
        List<Map<String, Object>> body = new ArrayList<>();
        for (PushMessage m : batch) {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("to", m.to());
            message.put("title", m.title());
            message.put("body", m.body());
            message.put("data", m.data());
            message.put("sound", "default");
            message.put("priority", "high");
            body.add(message);
        }
        try {
            return JSON.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("push body serialization failed", e);
        }
    }
}
