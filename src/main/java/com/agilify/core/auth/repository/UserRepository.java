package com.agilify.core.auth.repository;

import com.agilify.core.domain.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query(value = "SELECT * FROM users WHERE email = :email AND deleted_at IS NULL", nativeQuery = true)
    Optional<User> findByEmailAndDeletedAtIsNull(@Param("email") String email);

    @Query(value = "SELECT * FROM users WHERE deleted_at IS NULL AND email ILIKE CONCAT('%', :email, '%') LIMIT 10",
            nativeQuery = true)
    List<User> searchByEmailAndDeletedAtIsNull(@Param("email") String email);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM users WHERE email = :email AND deleted_at IS NULL)", nativeQuery = true)
    boolean existsByEmailAndDeletedAtIsNull(@Param("email") String email);

    @Query(value = "SELECT * FROM users WHERE id = :id AND deleted_at IS NULL", nativeQuery = true)
    Optional<User> findByIdAndDeletedAtIsNull(@Param("id") UUID id);
}