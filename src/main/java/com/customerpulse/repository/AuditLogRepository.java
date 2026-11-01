package com.customerpulse.repository;

import com.customerpulse.domain.AuditAction;
import com.customerpulse.domain.AuditLog;
import com.customerpulse.domain.AuditRecordType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Append-only: only save and read methods are exposed, so no code path can edit or delete an entry (NFR-07). */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog entry);

    long count();

    /** FR-13 review: newest first, every filter optional. */
    @EntityGraph(attributePaths = {"user", "customer"})
    @Query("""
            select a from AuditLog a
            where (:from is null or a.createdAt >= :from)
              and (:to is null or a.createdAt < :to)
              and (:userId is null or a.user.id = :userId)
              and (:recordType is null or a.recordType = :recordType)
              and (:action is null or a.action = :action)
              and (:customerId is null or a.customer.id = :customerId)
            order by a.createdAt desc, a.id desc
            """)
    Page<AuditLog> search(@Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          @Param("userId") Long userId,
                          @Param("recordType") AuditRecordType recordType,
                          @Param("action") AuditAction action,
                          @Param("customerId") Long customerId,
                          Pageable pageable);

    List<AuditLog> findByRecordTypeAndRecordIdOrderByIdAsc(AuditRecordType recordType, Long recordId);
}
