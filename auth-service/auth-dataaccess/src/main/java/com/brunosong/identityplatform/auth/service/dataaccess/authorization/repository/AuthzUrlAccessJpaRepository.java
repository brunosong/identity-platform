package com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository;

import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzUrlAccessEntity;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuthzUrlAccessJpaRepository extends JpaRepository<AuthzUrlAccessEntity, Long> {

    List<AuthzUrlAccessEntity> findByRealmAndActiveIsTrueOrderBySortOrderAscUrlPatternAsc(Realm realm);

    List<AuthzUrlAccessEntity> findByRealmAndUrlPatternAndHttpMethod(Realm realm, String urlPattern, String httpMethod);

    /**
     * 활성 URL 규칙 검색 페이지.
     *
     * @param keyword     소문자 LIKE 패턴(%kw%). URL 패턴 또는 매핑 권한코드에 걸린다. null 이면 전체.
     * @param category    매핑된 권한의 분류. null 이면 전체.
     * @param pageCategory 분류 필터가 페이지 호출 URL 을 뜻할 때 true — 그때는 권한 분류가 아니라
     *                     URL 패턴 접두어로 거른다. 두 조건을 한 쿼리에서 갈라야 해서 따로 받는다.
     */
    @Query("SELECT DISTINCT ua FROM AuthzUrlAccessEntity ua LEFT JOIN ua.permissions p "
            + "WHERE ua.realm = :realm AND ua.active = :active "
            + "AND (:keyword IS NULL OR LOWER(ua.urlPattern) LIKE :keyword "
            + "     OR LOWER(p.permissionCode) LIKE :keyword) "
            + "AND (:category IS NULL "
            + "     OR (:pageCategory = TRUE AND ua.urlPattern LIKE :pagePrefix) "
            + "     OR (:pageCategory = FALSE AND p.category = :category))")
    List<AuthzUrlAccessEntity> searchPage(@Param("realm") Realm realm,
                                          @Param("keyword") String keyword,
                                          @Param("category") String category,
                                          @Param("pageCategory") boolean pageCategory,
                                          @Param("pagePrefix") String pagePrefix,
                                          @Param("active") boolean active,
                                          Pageable pageable);

    /**
     * 같은 조건의 건수. 매핑 권한이 여러 개면 조인이 행을 늘리므로 URL 리소스 기준으로 센다
     * (DISTINCT 없이 세면 매핑 수만큼 부풀어 총 건수가 실제보다 커진다).
     */
    @Query("SELECT COUNT(DISTINCT ua) FROM AuthzUrlAccessEntity ua LEFT JOIN ua.permissions p "
            + "WHERE ua.realm = :realm AND ua.active = :active "
            + "AND (:keyword IS NULL OR LOWER(ua.urlPattern) LIKE :keyword "
            + "     OR LOWER(p.permissionCode) LIKE :keyword) "
            + "AND (:category IS NULL "
            + "     OR (:pageCategory = TRUE AND ua.urlPattern LIKE :pagePrefix) "
            + "     OR (:pageCategory = FALSE AND p.category = :category))")
    long countSearch(@Param("realm") Realm realm,
                     @Param("keyword") String keyword,
                     @Param("category") String category,
                     @Param("pageCategory") boolean pageCategory,
                     @Param("pagePrefix") String pagePrefix,
                     @Param("active") boolean active);

    /** 인가 캐시용 — 활성 URL 규칙의 (패턴, 메서드, 권한코드) 행. 매핑이 여러 건이면 행이 여러 개 나온다. */
    @Query("SELECT ua.urlPattern, ua.httpMethod, p.permissionCode "
            + "FROM AuthzUrlAccessEntity ua JOIN ua.permissions p "
            + "WHERE ua.realm = :realm AND ua.active = :active AND p.active = :active")
    List<Object[]> findActiveUrlPermissionRows(@Param("realm") Realm realm, @Param("active") boolean active);

    /** 특정 권한에 매핑된 활성 URL 규칙(권한 기준 역방향). permissionId 는 realm 내 유일하므로 realm 스코프 불필요. */
    @Query("SELECT ua FROM AuthzUrlAccessEntity ua JOIN ua.permissions p "
            + "WHERE p.permissionId = :permissionId AND ua.active = :active "
            + "ORDER BY ua.urlPattern, ua.httpMethod")
    List<AuthzUrlAccessEntity> findByPermissionId(@Param("permissionId") Long permissionId,
                                                  @Param("active") boolean active);
}
