package com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository;

import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzPermissionEntity;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AuthzPermissionJpaRepository extends JpaRepository<AuthzPermissionEntity, Long> {

    Optional<AuthzPermissionEntity> findByRealmAndPermissionCode(Realm realm, String permissionCode);

    List<AuthzPermissionEntity> findByRealmAndActiveIsTrueOrderByCategoryAscPermissionCodeAsc(Realm realm);

    /**
     * 활성 권한 검색. keyword 는 소문자 LIKE 패턴(%kw%)이며 null 이면 전체다.
     *
     * <p>필터를 DB 에 맡긴다 — 전건을 읽어 메모리에서 거르면 권한이 늘수록 응답이 같이 느려진다.
     */
    @Query("SELECT p FROM AuthzPermissionEntity p "
            + "WHERE p.realm = :realm AND p.active = :active "
            + "AND (:keyword IS NULL OR LOWER(p.permissionCode) LIKE :keyword "
            + "     OR LOWER(p.permissionName) LIKE :keyword "
            + "     OR LOWER(p.category) LIKE :keyword) "
            + "ORDER BY p.category ASC, p.permissionCode ASC")
    List<AuthzPermissionEntity> search(@Param("realm") Realm realm,
                                       @Param("keyword") String keyword,
                                       @Param("active") boolean active);

    /** 검색 결과의 한 페이지. 건수는 {@link #countSearch} 로 따로 센다. */
    @Query("SELECT p FROM AuthzPermissionEntity p "
            + "WHERE p.realm = :realm AND p.active = :active "
            + "AND (:keyword IS NULL OR LOWER(p.permissionCode) LIKE :keyword "
            + "     OR LOWER(p.permissionName) LIKE :keyword "
            + "     OR LOWER(p.category) LIKE :keyword)")
    List<AuthzPermissionEntity> searchPage(@Param("realm") Realm realm,
                                           @Param("keyword") String keyword,
                                           @Param("active") boolean active,
                                           Pageable pageable);

    @Query("SELECT COUNT(p) FROM AuthzPermissionEntity p "
            + "WHERE p.realm = :realm AND p.active = :active "
            + "AND (:keyword IS NULL OR LOWER(p.permissionCode) LIKE :keyword "
            + "     OR LOWER(p.permissionName) LIKE :keyword "
            + "     OR LOWER(p.category) LIKE :keyword)")
    long countSearch(@Param("realm") Realm realm,
                     @Param("keyword") String keyword,
                     @Param("active") boolean active);

    @Query("SELECT DISTINCT p.category FROM AuthzPermissionEntity p "
            + "WHERE p.realm = :realm AND p.active = :active AND p.category IS NOT NULL "
            + "ORDER BY p.category ASC")
    List<String> findActiveCategories(@Param("realm") Realm realm, @Param("active") boolean active);
}
