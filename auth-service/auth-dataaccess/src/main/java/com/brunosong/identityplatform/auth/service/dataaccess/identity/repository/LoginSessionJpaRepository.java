package com.brunosong.identityplatform.auth.service.dataaccess.identity.repository;

import com.brunosong.identityplatform.auth.service.dataaccess.identity.entity.LoginSessionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginSessionJpaRepository extends JpaRepository<LoginSessionJpaEntity, String> {
}
