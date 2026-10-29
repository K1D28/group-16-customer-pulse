package com.customerpulse.repository;

import com.customerpulse.domain.CaseStatus;
import com.customerpulse.domain.ServiceCase;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CaseRepository extends JpaRepository<ServiceCase, Long> {

    /** Cases the user is assigned to (userId = null means every case, for Managers). */
    @EntityGraph(attributePaths = {"customer", "createdBy"})
    @Query("""
            select c from ServiceCase c
            where c.status in :statuses
              and (:teamId is null or c.customer.owner.team.id = :teamId)
              and (:userId is null or exists (
                    select 1 from CaseAssignment a where a.serviceCase = c and a.user.id = :userId))
            order by c.createdAt desc
            """)
    List<ServiceCase> findInScope(@Param("userId") Long userId,
                                  @Param("teamId") Long teamId,
                                  @Param("statuses") Collection<CaseStatus> statuses);

    @EntityGraph(attributePaths = "createdBy")
    List<ServiceCase> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    long countByCreatedById(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ServiceCase c where c.customer.id = :customerId")
    void deleteByCustomerId(@Param("customerId") Long customerId);
}
