package com.customerpulse.domain;

/** Lifecycle status of a customer account (SRS 1.6). */
public enum CustomerStatus {
    NEGOTIATING("Negotiating", "st-negotiating"),
    ACTIVE("Active", "st-active"),
    HOLD("Hold", "st-hold"),
    INACTIVE("Inactive", "st-inactive"),
    LOST("Lost", "st-lost");

    private final String label;
    private final String css;

    CustomerStatus(String label, String css) {
        this.label = label;
        this.css = css;
    }

    public String getLabel() { return label; }
    public String getCss() { return css; }
}
