package com.luvin.push.client;

import java.util.List;
import java.util.Set;

public interface PushClient {

    /**
     * 알림을 보낸다. 전송 자체가 실패하면 예외를 던진다(호출부가 로그만 남긴다).
     *
     * @return 더 이상 쓸 수 없는 토큰(Expo DeviceNotRegistered). 호출부가 지운다
     */
    Set<String> send(List<PushMessage> messages);
}
