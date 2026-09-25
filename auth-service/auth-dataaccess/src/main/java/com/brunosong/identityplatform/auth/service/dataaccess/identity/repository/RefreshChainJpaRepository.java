package com.brunosong.identityplatform.auth.service.dataaccess.identity.repository;

import com.brunosong.identityplatform.auth.service.dataaccess.identity.entity.RefreshChainJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshChainJpaRepository extends JpaRepository<RefreshChainJpaEntity, String> {
}
