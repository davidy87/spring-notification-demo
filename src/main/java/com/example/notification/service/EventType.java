package com.example.notification.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventType {

    SUBSCRIPTION("connection"),
    EVENT("event");

    private final String eventName;
}
