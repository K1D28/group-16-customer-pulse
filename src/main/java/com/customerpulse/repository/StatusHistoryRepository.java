package com.customerpulse.repository;

import com.customerpulse.domain.StatusHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    @EntityGraph(attributePaths = "changedBy")
    List<StatusHistory> findByCustomerIdOrderByChangedAtDescIdDesc(Long customerId);

    /** FR-08: status transitions in a date range (customers that are not soft-deleted). */
    @EntityGraph(attributePaths = {"changedBy", "customer", "customer.owner"})
    @Query("""
            select h from StatusHistory h
            where h.customer.deletedAt is null
              and (:from is null or h.changedAt >= :from)
              and (:to is null or h.changedAt < :to)
            order by h.changedAt desc, h.id desc
            """)
    List<StatusHistory> findTransitions(@Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to,
                                        Pageable pageable);
}
