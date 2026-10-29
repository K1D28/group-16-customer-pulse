package com.customerpulse.repository;

import com.customerpulse.domain.Activity;
import com.customerpulse.domain.ActivityType;
import com.customerpulse.domain.Channel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    /** FR-03: timeline, newest first. */
    @EntityGraph(attributePaths = "user")
    List<Activity> findByCustomerIdOrderByActivityDateDesc(Long customerId);

    @EntityGraph(attributePaths = {"user", "customer"})
    @Query("""
            select a from Activity a
            where (:ownerId is null or a.customer.owner.id = :ownerId or a.user.id = :ownerId)
              and (:teamId is null or a.customer.owner.team.id = :teamId)
              and (:type is null or a.activityType = :type)
              and (:channel is null or a.channel = :channel)
              and (:q is null
                   or lower(a.outcome) like lower(concat('%', :q, '%'))
                   or lower(a.customer.companyName) like lower(concat('%', :q, '%')))
            order by a.activityDate desc
            """)
    Page<Activity> search(@Param("ownerId") Long ownerId,
                          @Param("teamId") Long teamId,
                          @Param("type") ActivityType type,
                          @Param("channel") Channel channel,
                          @Param("q") String q,
                          Pageable pageable);

    /** customer id -> date of the most recent activity. */
    @Query("select a.customer.id, max(a.activityDate) from Activity a group by a.customer.id")
    List<Object[]> lastActivityPerCustomer();

    @Query("""
            select count(a) from Activity a
            where (:ownerId is null or a.customer.owner.id = :ownerId)
              and (:teamId is null or a.customer.owner.team.id = :teamId)
              and a.activityDate >= :since
            """)
    long countSince(@Param("ownerId") Long ownerId, @Param("teamId") Long teamId, @Param("since") LocalDateTime since);

    @Query("select a.user.id, count(a) from Activity a where a.activityDate >= :since group by a.user.id")
    List<Object[]> countPerUserSince(@Param("since") LocalDateTime since);

    long countByUserId(Long userId);

    @EntityGraph(attributePaths = "user")
    List<Activity> findByServiceCaseIdOrderByActivityDateDesc(Long caseId);

    long countByServiceCaseId(Long caseId);

    /** Activities stay on the customer timeline when their case is deleted. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Activity a set a.serviceCase = null where a.serviceCase.id = :caseId")
    void detachCase(@Param("caseId") Long caseId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Activity a where a.customer.id = :customerId")
    void deleteByCustomerId(@Param("customerId") Long customerId);
}
