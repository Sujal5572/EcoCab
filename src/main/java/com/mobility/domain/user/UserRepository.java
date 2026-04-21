package com.mobility.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

// FIX: Was empty class. Must be JpaRepository interface.
public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByPhoneNumber(String phoneNumber);

    long countByStatus(User.UserStatus status);

    @Query("SELECT u FROM User u WHERE u.id = :userId AND u.status = 'ACTIVE'")
    Optional<User> findActiveById(@Param("userId") UUID userId);
}