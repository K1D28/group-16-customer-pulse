package com.customerpulse.repository;

import com.customerpulse.domain.Customer;
import com.customerpulse.domain.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Soft-deleted customers (deletedAt set) are left out of every query except findById. */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** FR-11: search by company or contact name, optional status. ownerId = null means all customers (Manager). */
    @EntityGraph(attributePaths = "owner")
    @Query("""
            select c from Customer c
            where c.deletedAt is null
              and (:ownerId is null or c.owner.id = :ownerId)
              and (:status is null or c.currentStatus = :status)
              and (:q is null
                   or lower(c.companyName) like lower(concat('%', :q, '%'))
                   or lower(c.contactName) like lower(concat('%', :q, '%')))
            """)
    Page<Customer> search(@Param("ownerId") Long ownerId,
                          @Param("status") CustomerStatus status,
                          @Param("q") String q,
                          Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    @Query("""
            select c from Customer c
            where c.deletedAt is null and (:ownerId is null or c.owner.id = :ownerId)
            order by c.companyName
            """)
    List<Customer> findInScope(@Param("ownerId") Long ownerId);

    /** FR-07: candidates for the stagnant check. */
    @EntityGraph(attributePaths = "owner")
    @Query("""
            select c from Customer c
            where c.deletedAt is null and (:ownerId is null or c.owner.id = :ownerId)
              and c.currentStatus in :statuses
            """)
    List<Customer> findInScopeWithStatus(@Param("ownerId") Long ownerId,
                                         @Param("statuses") Collection<CustomerStatus> statuses);

    @Query("""
            select c.currentStatus, count(c) from Customer c
            where c.deletedAt is null and (:ownerId is null or c.owner.id = :ownerId)
            group by c.currentStatus
            """)
    List<Object[]> countByStatus(@Param("ownerId") Long ownerId);

    /** FR-12: every non-deleted customer, across all owners. */
    @EntityGraph(attributePaths = "owner")
    List<Customer> findByDeletedAtIsNull();

    /** FR-10: a user who still owns (non-deleted) customers cannot be deactivated. */
    long countByOwnerIdAndDeletedAtIsNull(Long ownerId);
}
