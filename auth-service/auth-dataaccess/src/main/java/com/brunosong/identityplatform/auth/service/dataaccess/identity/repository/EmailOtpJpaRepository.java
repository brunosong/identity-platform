package com.brunosong.identityplatform.auth.service.dataaccess.identity.repository;

import com.brunosong.identityplatform.auth.service.dataaccess.identity.entity.EmailOtpJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailOtpJpaRepository extends JpaRepository<EmailOtpJpaEntity, Long> {

    Optional<EmailOtpJpaEntity> findFirstByEmailAndUsedFalseOrderByIdDesc(String email);

    Optional<EmailOtpJpaEntity> findFirstByEmailOrderByIdDesc(String email);
}
