package com.customerpulse.domain;

public enum ActivityType {
    CALL("Call", "call", Channel.PHONE),
    MEETING("Meeting", "groups", Channel.IN_PERSON),
    EMAIL("Email", "mail", Channel.EMAIL),
    CHAT("Chat", "chat", Channel.LINE),
    QUOTATION("Quotation", "request_quote", Channel.EMAIL),
    NOTE("Note", "sticky_note_2", Channel.OTHER);

    private final String label;
    private final String icon;
    private final Channel defaultChannel;

    ActivityType(String label, String icon, Channel defaultChannel) {
        this.label = label;
        this.icon = icon;
        this.defaultChannel = defaultChannel;
    }

    public String getLabel() { return label; }
    /** Material Symbols icon name. */
    public String getIcon() { return icon; }
    /** Channel pre-selected on the activity form when this type is chosen; the user can still change it. */
    public Channel getDefaultChannel() { return defaultChannel; }
}
