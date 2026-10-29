package com.customerpulse.domain;

public enum Channel {
    LINE("LINE"),
    EMAIL("Email"),
    PHONE("Phone"),
    ONLINE_MEETING("Online meeting"),
    IN_PERSON("In person"),
    OTHER("Other");

    private final String label;

    Channel(String label) { this.label = label; }

    public String getLabel() { return label; }
}
