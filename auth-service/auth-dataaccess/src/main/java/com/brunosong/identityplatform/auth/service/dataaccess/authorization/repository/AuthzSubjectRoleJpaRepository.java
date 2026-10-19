package com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository;

import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzRoleEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzSubjectRoleEntity;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 주체의 권한과 그 서비스(역할 → 권한 조인, 활성만). 서비스가 없는 권한은 뺀다.
     * 두 역할이 같은 권한을 주면 한 번만 나온다.
     */
    @Query("SELECT DISTINCT new com.brunosong.identityplatform.auth.service.dataaccess.authorization"
            + ".repository.ServicePermissionRow(p.serviceId, p.permissionCode) "
            + "FROM AuthzSubjectRoleEntity sr "
            + "JOIN AuthzRoleEntity r ON sr.roleId = r.roleId "
            + "JOIN r.permissions p "
            + "WHERE sr.realm = :realm AND sr.subjectId = :subjectId "
            + "AND r.active = :active AND p.active = :active AND p.serviceId IS NOT NULL "
            + "ORDER BY p.serviceId ASC, p.permissionCode ASC")
    List<ServicePermissionRow> findServicePermissions(@Param("realm") Realm realm,
                                                      @Param("subjectId") String subjectId,
                                                      @Param("active") boolean active);

    /** 주체의 역할 코드(활성 역할만). */
    @Query("SELECT r.roleCode FROM AuthzSubjectRoleEntity sr "
            + "JOIN AuthzRoleEntity r ON sr.roleId = r.roleId "
            + "WHERE sr.realm = :realm AND sr.subjectId = :subjectId AND r.active = :active")
    List<String> findRoleCodes(@Param("realm") Realm realm, @Param("subjectId") String subjectId,
                               @Param("active") boolean active);

}
