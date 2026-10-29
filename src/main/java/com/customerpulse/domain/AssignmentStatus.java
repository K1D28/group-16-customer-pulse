package com.customerpulse.domain;

public enum AssignmentStatus {
    ASSIGNED("Working on it"),
    DONE("Done");

    private final String label;

    AssignmentStatus(String label) { this.label = label; }

    public String getLabel() { return label; }
}
