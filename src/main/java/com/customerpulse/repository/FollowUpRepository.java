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

public interface FollowUpRepository extends JpaRepository<FollowUp, Long> {

    @EntityGraph(attributePaths = {"assignedUser", "activity"})
    List<FollowUp> findByCustomerIdOrderByDueDateAsc(Long customerId);

    /**
     * Follow-ups the user should see: assigned to them or on a customer they own.
     * userId = null means everything (Manager).
     */
    @EntityGraph(attributePaths = {"assignedUser", "customer", "customer.owner"})
    @Query("""
            select f from FollowUp f
            where (:userId is null or f.assignedUser.id = :userId or f.customer.owner.id = :userId)
              and (:teamId is null or f.customer.owner.team.id = :teamId)
              and f.status in :statuses
            order by f.dueDate asc
            """)
    List<FollowUp> findInScope(@Param("userId") Long userId,
                               @Param("teamId") Long teamId,
                               @Param("statuses") Collection<FollowUpStatus> statuses);

    /** FR-05 / NFR-09: PENDING follow-ups past their due date become OVERDUE. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update FollowUp f set f.status = com.customerpulse.domain.FollowUpStatus.OVERDUE " +
           "where f.status = com.customerpulse.domain.FollowUpStatus.PENDING and f.dueDate < :today")
    int markOverdue(@Param("today") LocalDate today);

    @Query("select f.customer.owner.id, count(f) from FollowUp f where f.status = :status group by f.customer.owner.id")
    List<Object[]> countPerOwner(@Param("status") FollowUpStatus status);

    long countByAssignedUserId(Long userId);

    /** On reassignment: hand the old owner's open follow-ups for this customer to the new owner. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update FollowUp f set f.assignedUser = :newUser
            where f.customer.id = :customerId and f.assignedUser.id = :oldUserId
              and f.status <> com.customerpulse.domain.FollowUpStatus.COMPLETED
            """)
    int moveOpenFollowUps(@Param("customerId") Long customerId, @Param("oldUserId") Long oldUserId,
                          @Param("newUser") com.customerpulse.domain.AppUser newUser);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update FollowUp f set f.activity = null where f.activity.id = :activityId")
    void detachActivity(@Param("activityId") Long activityId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FollowUp f where f.customer.id = :customerId")
    void deleteByCustomerId(@Param("customerId") Long customerId);
}
