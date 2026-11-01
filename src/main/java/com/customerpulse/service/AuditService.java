package com.customerpulse.service;

import com.customerpulse.domain.*;
import com.customerpulse.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * FR-13 / NFR-07: writes audit entries inside the caller's transaction (MANDATORY), so if the entry
 * cannot be saved, the change it describes is rolled back too. Passwords are never passed in here.
 */
@Service
public class AuditService {

    /** Shown instead of a password value (FR-10). */
    public static final String PASSWORD_CHANGED = "password changed";

    private final AuditLogRepository log;
    private final ObjectMapper json;

    public AuditService(AuditLogRepository log, ObjectMapper json) {
        this.log = log;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void created(AppUser by, AuditRecordType type, Long id, Customer customer, Map<String, Object> values) {
        write(by, AuditAction.CREATE, type, id, customer, values);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleted(AppUser by, AuditRecordType type, Long id, Customer customer, Map<String, Object> values) {
        write(by, AuditAction.DELETE, type, id, customer, values);
    }

    /** Records only the fields that changed, as [old, new]. Nothing is written when nothing changed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void updated(AppUser by, AuditRecordType type, Long id, Customer customer,
                        Map<String, Object> before, Map<String, Object> after) {
        updatedFields(by, type, id, customer, diff(before, after));
    }

    /** The fields whose value changed, as field -> [old, new]. */
    public static Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> diff = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : after.entrySet()) {
            Object old = before.get(e.getKey());
            if (!Objects.equals(old, e.getValue())) diff.put(e.getKey(), Arrays.asList(old, e.getValue()));
        }
        return diff;
    }

    /** Records an update whose change list is already known (e.g. a password reset). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void updatedFields(AppUser by, AuditRecordType type, Long id, Customer customer, Map<String, Object> diff) {
        if (!diff.isEmpty()) write(by, AuditAction.UPDATE, type, id, customer, diff);
    }

    private void write(AppUser by, AuditAction action, AuditRecordType type, Long id, Customer customer,
                       Map<String, Object> changes) {
        AuditLog entry = new AuditLog();
        entry.setUser(by);
        entry.setAction(action);
        entry.setRecordType(type);
        entry.setRecordId(id);
        entry.setCustomer(customer);
        try {
            entry.setChanges(json.writeValueAsString(changes));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write the audit entry", e);
        }
        log.save(entry);
    }

    /** Reads an entry's JSON back for the audit page: field -> value, or field -> [old, new]. */
    public Map<String, Object> read(AuditLog entry) {
        try {
            return json.readValue(entry.getChanges(), new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return Map.of("changes", entry.getChanges());
        }
    }

    // ---- snapshots: the audited columns of each record type, using the data-dictionary names ----

    public static Map<String, Object> snapshot(Customer c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("company_name", c.getCompanyName());
        m.put("contact_name", c.getContactName());
        m.put("email", c.getEmail());
        m.put("phone", c.getPhone());
        m.put("current_status", c.getCurrentStatus() == null ? null : c.getCurrentStatus().name());
        m.put("owner_id", c.getOwner() == null ? null : c.getOwner().getId());
        return m;
    }

    public static Map<String, Object> snapshot(Activity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activity_type", a.getActivityType() == null ? null : a.getActivityType().name());
        m.put("channel", a.getChannel() == null ? null : a.getChannel().name());
        m.put("outcome", a.getOutcome());
        m.put("activity_date", a.getActivityDate() == null ? null : a.getActivityDate().toString());
        m.put("user_id", a.getUser() == null ? null : a.getUser().getId());
        return m;
    }

    public static Map<String, Object> snapshot(FollowUp f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activity_id", f.getActivity() == null ? null : f.getActivity().getId());
        m.put("assigned_user_id", f.getAssignedUser() == null ? null : f.getAssignedUser().getId());
        m.put("due_date", f.getDueDate() == null ? null : f.getDueDate().toString());
        m.put("status", f.getStatus() == null ? null : f.getStatus().name());
        m.put("notes", f.getNotes());
        m.put("completed_at", f.getCompletedAt() == null ? null : f.getCompletedAt().toString());
        return m;
    }

    /** Never includes the password hash. */
    public static Map<String, Object> snapshot(AppUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("full_name", u.getFullName());
        m.put("email", u.getEmail());
        m.put("role", u.getRole() == null ? null : u.getRole().name());
        m.put("is_active", u.isActive());
        return m;
    }
}
