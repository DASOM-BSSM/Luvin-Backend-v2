package com.luvin.push.config;

import com.luvin.push.client.ExpoPushClient;
import com.luvin.push.client.NoopPushClient;
import com.luvin.push.client.PushClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PushProperties.class)
public class PushConfig {

    @Bean
    public PushClient pushClient(PushProperties properties) {
        return PushProperties.CLIENT_EXPO.equals(properties.client())
                ? new ExpoPushClient(properties)
                : new NoopPushClient();
    }
}
