package com.customerpulse.repository;

import com.customerpulse.domain.Customer;
import com.customerpulse.domain.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** FR-11: search by company or contact name and status. ownerId = null means "all customers" (Manager). */
    @EntityGraph(attributePaths = "owner")
    @Query("""
            select c from Customer c
            where (:ownerId is null or c.owner.id = :ownerId)
              and (:teamId is null or c.owner.team.id = :teamId)
              and (:status is null or c.currentStatus = :status)
              and (:q is null
                   or lower(c.companyName) like lower(concat('%', :q, '%'))
                   or lower(c.contactName) like lower(concat('%', :q, '%')))
            """)
    Page<Customer> search(@Param("ownerId") Long ownerId,
                          @Param("teamId") Long teamId,
                          @Param("status") CustomerStatus status,
                          @Param("q") String q,
                          Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    @Query("""
            select c from Customer c
            where (:ownerId is null or c.owner.id = :ownerId) and (:teamId is null or c.owner.team.id = :teamId)
            order by c.companyName
            """)
    List<Customer> findInScope(@Param("ownerId") Long ownerId, @Param("teamId") Long teamId);

    @Query("""
            select c.currentStatus, count(c) from Customer c
            where (:ownerId is null or c.owner.id = :ownerId) and (:teamId is null or c.owner.team.id = :teamId)
            group by c.currentStatus
            """)
    List<Object[]> countByStatus(@Param("ownerId") Long ownerId, @Param("teamId") Long teamId);

    @EntityGraph(attributePaths = "owner")
    List<Customer> findByOwnerTeamId(Long teamId);

    long countByOwnerId(Long ownerId);
}
