package com.customerpulse.web;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Date helpers for templates, used as ${@fmt.ago(date)}. */
@Component("fmt")
public class Fmt {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    public String date(LocalDate d) { return d == null ? "—" : DATE.format(d); }

    public String date(LocalDateTime d) { return d == null ? "—" : DATE.format(d); }

    public String dateTime(LocalDateTime d) { return d == null ? "—" : DATE_TIME.format(d); }

    /** "today", "yesterday", "5 days ago". */
    public String ago(LocalDateTime d) {
        if (d == null) return "never";
        long days = ChronoUnit.DAYS.between(d.toLocalDate(), LocalDate.now());
        if (days < 0) return "in " + (-days) + (days == -1 ? " day" : " days");
        if (days == 0) return "today";
        if (days == 1) return "yesterday";
        return days + " days ago";
    }

    /** "Due today", "Due in 3 days", "4 days overdue". */
    public String due(LocalDate d) {
        if (d == null) return "";
        long days = ChronoUnit.DAYS.between(LocalDate.now(), d);
        if (days == 0) return "Due today";
        if (days == 1) return "Due tomorrow";
        if (days > 1) return "Due in " + days + " days";
        return (-days) + (days == -1 ? " day overdue" : " days overdue");
    }
}
