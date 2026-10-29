package com.customerpulse.domain;

public enum CasePriority {
    LOW("Low", "pr-low"),
    MEDIUM("Medium", "pr-medium"),
    HIGH("High", "pr-high");

    private final String label;
    private final String css;

    CasePriority(String label, String css) {
        this.label = label;
        this.css = css;
    }

    public String getLabel() { return label; }
    public String getCss() { return css; }
}
