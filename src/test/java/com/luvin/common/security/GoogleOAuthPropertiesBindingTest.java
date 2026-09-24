package com.luvin.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleOAuthPropertiesBindingTest {

    @Configuration
    @EnableConfigurationProperties(GoogleOAuthProperties.class)
    static class Config {
    }

    @Test
    void commaSeparatedEnvValueBindsToList() {
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues("app.oauth.google.client-ids=android-id,ios-id,web-id")
                .run(context -> {
                    GoogleOAuthProperties props = context.getBean(GoogleOAuthProperties.class);
                    System.out.println("clientIds=" + props.clientIds());
                    assertEquals(3, props.clientIds().size());
                    assertTrue(props.clientIds().contains("android-id"));
                    assertTrue(props.clientIds().contains("ios-id"));
                    assertTrue(props.clientIds().contains("web-id"));
                });
    }
}
