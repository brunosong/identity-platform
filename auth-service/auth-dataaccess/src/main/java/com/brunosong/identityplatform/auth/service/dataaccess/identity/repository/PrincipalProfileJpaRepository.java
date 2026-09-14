package com.brunosong.identityplatform.auth.service.dataaccess.identity.repository;

import com.brunosong.identityplatform.auth.service.dataaccess.identity.entity.PrincipalProfileJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** 프로필은 신원당 하나뿐이라 PK 조회면 충분하다. */
public interface PrincipalProfileJpaRepository extends JpaRepository<PrincipalProfileJpaEntity, String> {
}
