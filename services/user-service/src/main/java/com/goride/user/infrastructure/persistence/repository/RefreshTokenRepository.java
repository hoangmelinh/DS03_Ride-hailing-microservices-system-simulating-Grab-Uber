package com.goride.user.infrastructure.persistence.repository;

import com.goride.user.infrastructure.persistence.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    List<RefreshTokenEntity> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
