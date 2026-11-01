package com.customerpulse.repository;

import com.customerpulse.domain.FollowUp;
import com.customerpulse.domain.FollowUpStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** Soft-deleted follow-ups, and follow-ups of soft-deleted customers, are left out. */
public interface FollowUpRepository extends JpaRepository<FollowUp, Long> {

    @EntityGraph(attributePaths = {"assignedUser", "activity"})
    List<FollowUp> findByCustomerIdAndDeletedAtIsNullOrderByDueDateAsc(Long customerId);

    /** Follow-ups on customers in scope (ownerId = null means all customers, for Managers), oldest due date first. */
    @EntityGraph(attributePaths = {"assignedUser", "customer", "customer.owner"})
    @Query("""
            select f from FollowUp f
            where f.deletedAt is null and f.customer.deletedAt is null
              and (:ownerId is null or f.customer.owner.id = :ownerId)
              and f.status in :statuses
            order by f.dueDate asc
            """)
    List<FollowUp> findInScope(@Param("ownerId") Long ownerId,
                               @Param("statuses") Collection<FollowUpStatus> statuses);

    /** FR-05 daily job: PENDING follow-ups whose due date is before today become OVERDUE (not audited). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update FollowUp f set f.status = com.customerpulse.domain.FollowUpStatus.OVERDUE " +
           "where f.status = com.customerpulse.domain.FollowUpStatus.PENDING and f.dueDate < :today")
    int markOverdue(@Param("today") LocalDate today);

    @Query("""
            select f.customer.owner.id, count(f) from FollowUp f
            where f.status = :status and f.deletedAt is null and f.customer.deletedAt is null
            group by f.customer.owner.id
            """)
    List<Object[]> countPerOwner(@Param("status") FollowUpStatus status);

    /** FR-09: the customer's open (PENDING and OVERDUE) follow-ups, which move to a new owner. */
    @Query("""
            select f from FollowUp f
            where f.customer.id = :customerId and f.deletedAt is null
              and f.status <> com.customerpulse.domain.FollowUpStatus.COMPLETED
            """)
    List<FollowUp> findOpenByCustomer(@Param("customerId") Long customerId);
}
