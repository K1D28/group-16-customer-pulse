package com.customerpulse.domain;

public enum Role {
    MANAGER("Manager"),
    STAFF("Staff");

    private final String label;

    Role(String label) { this.label = label; }

    public String getLabel() { return label; }
}
