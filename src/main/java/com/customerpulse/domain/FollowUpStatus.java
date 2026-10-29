package com.customerpulse.domain;

public enum FollowUpStatus {
    PENDING("Pending", "fu-pending"),
    COMPLETED("Completed", "fu-completed"),
    OVERDUE("Overdue", "fu-overdue");

    private final String label;
    private final String css;

    FollowUpStatus(String label, String css) {
        this.label = label;
        this.css = css;
    }

    public String getLabel() { return label; }
    public String getCss() { return css; }
}
