package com.goride.user.infrastructure.persistence.repository;

import com.goride.user.domain.model.UserStatus;
import com.goride.user.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByPhone(String phone);

    Optional<UserEntity> findByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    Page<UserEntity> findByStatus(UserStatus status, Pageable pageable);
}
