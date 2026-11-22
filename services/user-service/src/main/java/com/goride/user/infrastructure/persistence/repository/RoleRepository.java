package com.goride.user.infrastructure.persistence.repository;

import com.goride.user.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, Short> {

    Optional<RoleEntity> findByCode(String code);
}
