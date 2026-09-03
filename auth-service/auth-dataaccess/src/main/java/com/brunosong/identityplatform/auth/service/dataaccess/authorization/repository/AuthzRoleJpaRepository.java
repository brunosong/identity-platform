package com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository;

import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzRoleEntity;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AuthzRoleJpaRepository extends JpaRepository<AuthzRoleEntity, Long> {

    Optional<AuthzRoleEntity> findByRealmAndRoleCode(Realm realm, String roleCode);

    List<AuthzRoleEntity> findByRealmAndActiveIsTrueOrderByRoleId(Realm realm);

    /** 활성 역할 검색. keyword 는 소문자 LIKE 패턴(%kw%)이며 null 이면 전체다. */
    @Query("SELECT r FROM AuthzRoleEntity r "
            + "WHERE r.realm = :realm AND r.active = :active "
            + "AND (:keyword IS NULL OR LOWER(r.roleCode) LIKE :keyword OR LOWER(r.roleName) LIKE :keyword) "
            + "ORDER BY r.roleId ASC")
    List<AuthzRoleEntity> search(@Param("realm") Realm realm,
                                 @Param("keyword") String keyword,
                                 @Param("active") boolean active);
}
