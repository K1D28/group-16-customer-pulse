package com.customerpulse.repository;

import com.customerpulse.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<AppUser> findAllByOrderByFullNameAsc();
    List<AppUser> findByTeamIdOrderByFullNameAsc(Long teamId);
    List<AppUser> findByTeamIdAndActiveTrueOrderByFullNameAsc(Long teamId);
}
