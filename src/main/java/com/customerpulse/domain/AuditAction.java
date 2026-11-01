package com.customerpulse.domain;

public enum AuditAction {
    CREATE("Create", "fu-completed"),
    UPDATE("Update", "fu-pending"),
    DELETE("Delete", "fu-overdue");

    private final String label;
    private final String css;

    AuditAction(String label, String css) {
        this.label = label;
        this.css = css;
    }

    public String getLabel() { return label; }
    public String getCss() { return css; }
}
