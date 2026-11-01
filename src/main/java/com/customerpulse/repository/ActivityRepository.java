package com.customerpulse.repository;

import com.customerpulse.domain.Activity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Soft-deleted activities, and activities of soft-deleted customers, are left out. */
public interface ActivityRepository extends JpaRepository<Activity, Long> {

    /** FR-03: timeline, newest first. */
    @EntityGraph(attributePaths = "user")
    List<Activity> findByCustomerIdAndDeletedAtIsNullOrderByActivityDateDesc(Long customerId);

    /** FR-08: activities in a date range, newest first. ownerId = null means all customers. */
    @EntityGraph(attributePaths = {"user", "customer", "customer.owner"})
    @Query("""
            select a from Activity a
            where a.deletedAt is null and a.customer.deletedAt is null
              and (:ownerId is null or a.customer.owner.id = :ownerId)
              and (:from is null or a.activityDate >= :from)
              and (:to is null or a.activityDate < :to)
            order by a.activityDate desc
            """)
    List<Activity> findRecent(@Param("ownerId") Long ownerId,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to,
                              Pageable pageable);

    /** customer id -> date of the latest non-deleted activity (FR-07 stagnant check). */
    @Query("select a.customer.id, max(a.activityDate) from Activity a where a.deletedAt is null group by a.customer.id")
    List<Object[]> lastActivityPerCustomer();

    @Query("""
            select count(a) from Activity a
            where a.deletedAt is null and a.customer.deletedAt is null
              and (:ownerId is null or a.customer.owner.id = :ownerId)
              and a.activityDate >= :since
            """)
    long countSince(@Param("ownerId") Long ownerId, @Param("since") LocalDateTime since);

    @Query("""
            select a.user.id, count(a) from Activity a
            where a.deletedAt is null and a.customer.deletedAt is null and a.activityDate >= :since
            group by a.user.id
            """)
    List<Object[]> countPerUserSince(@Param("since") LocalDateTime since);
}
