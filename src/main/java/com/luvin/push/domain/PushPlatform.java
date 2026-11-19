package com.luvin.push.domain;

public enum PushPlatform {
    IOS,
    ANDROID;

    /** 요청의 "ios" | "android"(대소문자 무시). */
    public static PushPlatform fromWire(String value) {
        return PushPlatform.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
