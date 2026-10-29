package com.customerpulse.domain;

public enum ActivityType {
    CALL("Call", "call"),
    MEETING("Meeting", "groups"),
    EMAIL("Email", "mail"),
    CHAT("Chat", "chat"),
    QUOTATION("Quotation", "request_quote"),
    NOTE("Note", "sticky_note_2");

    private final String label;
    private final String icon;

    ActivityType(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String getLabel() { return label; }
    /** Material Symbols icon name. */
    public String getIcon() { return icon; }
}
