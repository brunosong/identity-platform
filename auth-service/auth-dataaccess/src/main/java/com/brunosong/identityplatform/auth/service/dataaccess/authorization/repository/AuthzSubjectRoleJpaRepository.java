package com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository;

import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzRoleEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzSubjectRoleEntity;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AuthzSubjectRoleJpaRepository
        extends JpaRepository<AuthzSubjectRoleEntity, AuthzSubjectRoleEntity.AuthzSubjectRoleId> {

    List<AuthzSubjectRoleEntity> findByRealmAndSubjectId(Realm realm, String subjectId);

    void deleteByRealmAndSubjectId(Realm realm, String subjectId);

    boolean existsByRealmAndSubjectIdAndRoleId(Realm realm, String subjectId, Long roleId);

    /**
     * 주체에 배정된 역할. 배정 행을 읽고 역할을 하나씩 지연 로딩하면 역할 수만큼 쿼리가 나가므로
     * 조인해서 한 번에 읽는다. 활성 여부는 거르지 않는다 — 배정 그대로 보여주는 화면용이다.
     */
    @Query("SELECT r FROM AuthzSubjectRoleEntity sr JOIN AuthzRoleEntity r ON sr.roleId = r.roleId "
            + "WHERE sr.realm = :realm AND sr.subjectId = :subjectId ORDER BY r.roleId ASC")
    List<AuthzRoleEntity> findAssignedRoles(@Param("realm") Realm realm, @Param("subjectId") String subjectId);

    /** 주체의 모든 권한 코드(역할 → 권한 조인, 활성만). */
    @Query("SELECT DISTINCT p.permissionCode FROM AuthzSubjectRoleEntity sr "
            + "JOIN AuthzRoleEntity r ON sr.roleId = r.roleId "
            + "JOIN r.permissions p "
            + "WHERE sr.realm = :realm AND sr.subjectId = :subjectId "
            + "AND r.active = :active AND p.active = :active")
    List<String> findPermissionCodes(@Param("realm") Realm realm, @Param("subjectId") String subjectId,
                                     @Param("active") boolean active);

    /** 주체의 역할 코드(활성 역할만). */
    @Query("SELECT r.roleCode FROM AuthzSubjectRoleEntity sr "
            + "JOIN AuthzRoleEntity r ON sr.roleId = r.roleId "
            + "WHERE sr.realm = :realm AND sr.subjectId = :subjectId AND r.active = :active")
    List<String> findRoleCodes(@Param("realm") Realm realm, @Param("subjectId") String subjectId,
                               @Param("active") boolean active);

    /**
     * 주체의 역할 코드(활성 역할만) — 토큰의 {@code realm_access.roles}.
     *
     * <p>역할은 서비스로 갈리지 않는다. "이 사람이 조직에서 맡은 일" 이고 그것은 서비스마다
     * 달라지지 않기 때문이다. 갈리는 것은 아래 권한이다.
     */
    @Query("SELECT DISTINCT r.roleCode FROM AuthzSubjectRoleEntity sr "
            + "JOIN AuthzRoleEntity r ON sr.roleId = r.roleId "
            + "WHERE sr.realm = :realm AND sr.subjectId = :subjectId AND r.active = :active")
    List<String> findRealmRoleCodes(@Param("realm") Realm realm, @Param("subjectId") String subjectId,
                                    @Param("active") boolean active);

    /**
     * 주체가 <b>그 서비스들에 대해</b> 가진 권한 코드(활성만).
     *
     * <p>역할 → 권한을 조인해 나온 결과를 서비스별로 가른다. 토큰의
     * {@code resource_access.{serviceId}.roles} 가 된다.
     *
     * <p>요청한 서비스만 읽는다 — 토큰의 {@code aud} 에 있는 것들이다. 전부 읽으면 토큰이
     * realm 전체 크기로 자란다.
     */
    @Query("SELECT DISTINCT p.serviceId, p.permissionCode FROM AuthzSubjectRoleEntity sr "
            + "JOIN AuthzRoleEntity r ON sr.roleId = r.roleId "
            + "JOIN r.permissions p "
            + "WHERE sr.realm = :realm AND sr.subjectId = :subjectId "
            + "AND p.serviceId IN :clientIds AND r.active = :active AND p.active = :active")
    List<Object[]> findClientPermissionCodes(@Param("realm") Realm realm,
                                             @Param("subjectId") String subjectId,
                                             @Param("clientIds") Collection<String> clientIds,
                                             @Param("active") boolean active);
}
