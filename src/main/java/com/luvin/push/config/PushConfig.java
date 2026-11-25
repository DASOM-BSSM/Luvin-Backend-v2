package com.luvin.push.config;

import com.luvin.push.client.ExpoPushClient;
import com.luvin.push.client.NoopPushClient;
import com.luvin.push.client.PushClient;
import com.luvin.push.client.QuietHoursPushClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(PushProperties.class)
public class PushConfig {

    /** 실제 클라이언트(expo | noop) 앞에 발송 시간 제한(KST 06:00~23:59)을 건다. Clock 빈이 없으면 시스템 시계를 쓴다(테스트가 바꿔 낀다). */
    @Bean
    public PushClient pushClient(PushProperties properties, ObjectProvider<Clock> clock) {
        PushClient client = PushProperties.CLIENT_EXPO.equals(properties.client())
                ? new ExpoPushClient(properties)
                : new NoopPushClient();
        return new QuietHoursPushClient(client, clock.getIfAvailable(Clock::systemUTC));
    }
}
