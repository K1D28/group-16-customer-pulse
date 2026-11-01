package com.customerpulse.domain;

public enum AuditRecordType {
    CUSTOMER("Customer"),
    ACTIVITY("Activity"),
    FOLLOW_UP("Follow-up"),
    USER("User");

    private final String label;

    AuditRecordType(String label) { this.label = label; }

    public String getLabel() { return label; }
}
