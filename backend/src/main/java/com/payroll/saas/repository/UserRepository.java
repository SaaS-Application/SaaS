package com.payroll.saas.repository;

import com.payroll.saas.entity.User;
import com.payroll.saas.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @EntityGraph(attributePaths = "company")
    Optional<User> findByUsernameIgnoreCase(String username);
    @EntityGraph(attributePaths = "company")
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByResetToken(String token);
    Optional<User> findByRole(UserRole role);
    List<User> findByCompanyId(Long companyId);
}
