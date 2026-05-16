package com.connectsphere.notification.entity;

public enum TargetType {
    POST,
    COMMENT,
    USER // Adding USER since FOLLOW implies target is a User
}
