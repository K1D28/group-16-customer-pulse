package com.customerpulse.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One audit entry (table: audit_log, FR-13). Append-only: nothing in the application edits or deletes it.
 * {@code changes} is JSON: new values for CREATE, {"field": [old, new]} for UPDATE, values at deletion for DELETE.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long id;

    /** The user who made the change. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 20, updatable = false)
    private AuditRecordType recordType;

    @Column(name = "record_id", nullable = false, updatable = false)
    private Long recordId;

    /** Related customer; null for user records. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", updatable = false)
    private Customer customer;

    @Column(nullable = false, length = 4000, updatable = false)
    private String changes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public AuditAction getAction() { return action; }
    public void setAction(AuditAction action) { this.action = action; }
    public AuditRecordType getRecordType() { return recordType; }
    public void setRecordType(AuditRecordType recordType) { this.recordType = recordType; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public String getChanges() { return changes; }
    public void setChanges(String changes) { this.changes = changes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
