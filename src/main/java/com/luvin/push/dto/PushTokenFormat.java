package com.luvin.push.dto;

final class PushTokenFormat {

    /** Expo 푸시 토큰. FCM/APNs 기기 토큰은 받지 않는다(Expo 푸시 서비스로만 보낸다). */
    static final String EXPO_TOKEN = "^Expo(nent)?PushToken\\[[^\\]\\s]+\\]$";

    private PushTokenFormat() {
    }
}
