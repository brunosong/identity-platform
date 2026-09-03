package com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository;

import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzRevisionEntity;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthzRevisionJpaRepository extends JpaRepository<AuthzRevisionEntity, Realm> {

    @Modifying
    @Query("UPDATE AuthzRevisionEntity r SET r.revisionNo = r.revisionNo + 1, r.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE r.realm = :realm")
    int bump(@Param("realm") Realm realm);

    @Query("SELECT r.revisionNo FROM AuthzRevisionEntity r WHERE r.realm = :realm")
    Long currentRevision(@Param("realm") Realm realm);
}
