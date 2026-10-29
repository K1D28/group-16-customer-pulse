package com.customerpulse.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One employee assigned to a case, and whether their part is done (table: case_assignments). */
@Entity
@Table(name = "case_assignments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"case_id", "user_id"}))
public class CaseAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private ServiceCase serviceCase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssignmentStatus status = AssignmentStatus.ASSIGNED;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    @Column(name = "done_at")
    private LocalDateTime doneAt;

    @Column(name = "done_note", length = 255)
    private String doneNote;

    @PrePersist
    void onCreate() {
        if (assignedAt == null) assignedAt = LocalDateTime.now();
    }

    public boolean isDone() { return status == AssignmentStatus.DONE; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ServiceCase getServiceCase() { return serviceCase; }
    public void setServiceCase(ServiceCase serviceCase) { this.serviceCase = serviceCase; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public AssignmentStatus getStatus() { return status; }
    public void setStatus(AssignmentStatus status) { this.status = status; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
    public LocalDateTime getDoneAt() { return doneAt; }
    public void setDoneAt(LocalDateTime doneAt) { this.doneAt = doneAt; }
    public String getDoneNote() { return doneNote; }
    public void setDoneNote(String doneNote) { this.doneNote = doneNote; }
}
