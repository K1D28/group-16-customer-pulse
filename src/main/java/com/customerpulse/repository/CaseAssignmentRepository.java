package com.customerpulse.repository;

import com.customerpulse.domain.CaseAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CaseAssignmentRepository extends JpaRepository<CaseAssignment, Long> {

    @EntityGraph(attributePaths = "user")
    List<CaseAssignment> findByServiceCaseIdOrderByIdAsc(Long caseId);

    @EntityGraph(attributePaths = "user")
    List<CaseAssignment> findByServiceCaseIdIn(Collection<Long> caseIds);

    Optional<CaseAssignment> findByServiceCaseIdAndUserId(Long caseId, Long userId);

    boolean existsByServiceCaseIdAndUserId(Long caseId, Long userId);

    /** The user's unfinished parts on cases that are still open. */
    @EntityGraph(attributePaths = {"serviceCase", "serviceCase.customer"})
    @Query("""
            select a from CaseAssignment a
            where a.user.id = :userId
              and a.status = com.customerpulse.domain.AssignmentStatus.ASSIGNED
              and a.serviceCase.status <> com.customerpulse.domain.CaseStatus.CLOSED
            order by a.serviceCase.createdAt desc
            """)
    List<CaseAssignment> findOpenForUser(@Param("userId") Long userId);

    long countByUserId(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CaseAssignment a where a.serviceCase.id = :caseId")
    void deleteByCaseId(@Param("caseId") Long caseId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CaseAssignment a where a.serviceCase.id in (select c.id from ServiceCase c where c.customer.id = :customerId)")
    void deleteByCustomerId(@Param("customerId") Long customerId);
}
