package com.luvin.push.client;

import java.util.Map;

/** 기기 하나로 보낼 알림. data는 앱이 알림을 눌렀을 때 받는 값이다. */
public record PushMessage(String to, String title, String body, Map<String, Object> data) {
}
