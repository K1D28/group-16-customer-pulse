package com.customerpulse.repository;

import com.customerpulse.domain.StatusHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    @EntityGraph(attributePaths = "changedBy")
    List<StatusHistory> findByCustomerIdOrderByChangedAtDesc(Long customerId);

    /** FR-08: recent status transitions, optionally filtered by owner and date range. */
    @EntityGraph(attributePaths = {"changedBy", "customer", "customer.owner"})
    @Query("""
            select h from StatusHistory h
            where (:ownerId is null or h.customer.owner.id = :ownerId)
              and (:teamId is null or h.customer.owner.team.id = :teamId)
              and (:from is null or h.changedAt >= :from)
              and (:to is null or h.changedAt < :to)
            order by h.changedAt desc
            """)
    List<StatusHistory> findTransitions(@Param("ownerId") Long ownerId,
                                        @Param("teamId") Long teamId,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to,
                                        Pageable pageable);

    long countByChangedById(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from StatusHistory h where h.customer.id = :customerId")
    void deleteByCustomerId(@Param("customerId") Long customerId);
}
