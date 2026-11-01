package com.customerpulse.repository;

import com.customerpulse.domain.AppUser;
import com.customerpulse.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    List<AppUser> findAllByOrderByFullNameAsc();
    List<AppUser> findByActiveTrueOrderByFullNameAsc();
    long countByRoleAndActiveTrue(Role role);
}
