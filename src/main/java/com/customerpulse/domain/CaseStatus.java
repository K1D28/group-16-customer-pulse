package com.customerpulse.domain;

public enum CaseStatus {
    OPEN("Open", "cs-open"),
    IN_PROGRESS("In progress", "cs-progress"),
    CLOSED("Closed", "cs-closed");

    private final String label;
    private final String css;

    CaseStatus(String label, String css) {
        this.label = label;
        this.css = css;
    }

    public String getLabel() { return label; }
    public String getCss() { return css; }
}
