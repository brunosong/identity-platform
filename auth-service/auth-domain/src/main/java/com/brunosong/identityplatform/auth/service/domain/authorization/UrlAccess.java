package com.brunosong.identityplatform.auth.service.domain.authorization;

import com.brunosong.identityplatform.auth.service.domain.authorization.valueobject.HttpMethodPattern;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * URL 접근 규칙 — realm 으로 스코프된 (URL 패턴, HTTP 메서드) 리소스.
 * 이 리소스를 허용하는 권한코드 목록(N:M, OR)은 읽기 투영으로만 싣는다 — 매핑 편집은 권한 기준에서 한다.
 *
 * <p>리소스 좌표(realm, urlPattern, httpMethod)는 생성 후 바뀌지 않는다. 좌표가 바뀌면 그것은 다른
 * 리소스이고, 붙어 있던 권한 매핑이 조용히 다른 URL 로 옮겨가는 사고가 된다.
 */
@Getter
public class UrlAccess {

    /** 페이지 호출 URL 의 패턴 접두어. 이 접두어로 시작하면 권한 분류와 별개로 다룬다. */
    public static final String PAGE_URL_PREFIX = "/page";

    /** 페이지 호출 URL 의 분류 이름. 권한에서 유도하지 않고 URL 모양으로 정해지는 유일한 분류다. */
    public static final String PAGE_CATEGORY = "PAGE";

    /** null = 아직 저장되지 않은 신규. */
    private final Long urlAccessId;
    private final Realm realm;
    private final String urlPattern;
    private final HttpMethodPattern httpMethod;
    private final LocalDateTime createdAt;

    private String description;
    private boolean active;
    private int sortOrder;

    /** 이 URL 을 허용하는 권한코드들(정렬됨). 읽기 투영이라 여기서 변경하지 않는다. */
    private final List<String> permissionCodes;

    private UrlAccess(Long urlAccessId, Realm realm, String urlPattern, HttpMethodPattern httpMethod,
                      String description, boolean active, int sortOrder, LocalDateTime createdAt,
                      Collection<String> permissionCodes) {
        this.urlAccessId = urlAccessId;
        this.realm = realm;
        this.urlPattern = urlPattern;
        this.httpMethod = httpMethod;
        this.description = description;
        this.active = active;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
        this.permissionCodes = permissionCodes == null ? List.of() : List.copyOf(permissionCodes);
    }

    public static UrlAccess create(Realm realm, String urlPattern, String httpMethod,
                                   String description, int sortOrder) {
        if (realm == null) throw new IllegalArgumentException("realm must not be null");
        return new UrlAccess(null, realm, requirePattern(urlPattern), HttpMethodPattern.of(httpMethod),
                normalize(description), true, sortOrder, null, null);
    }

    public static UrlAccess restore(Long urlAccessId, Realm realm, String urlPattern, String httpMethod,
                                    String description, boolean active, int sortOrder,
                                    LocalDateTime createdAt, Collection<String> permissionCodes) {
        return new UrlAccess(urlAccessId, realm, urlPattern, HttpMethodPattern.of(httpMethod),
                description, active, sortOrder, createdAt, permissionCodes);
    }

    public List<String> getPermissionCodes() {
        return Collections.unmodifiableList(permissionCodes);
    }

    public void describeAs(String description, int sortOrder) {
        this.description = normalize(description);
        this.sortOrder = sortOrder;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    /**
     * 페이지 호출 URL 인가. 화면 분류와 검색 필터가 같은 판정을 써야 해서 여기에 둔다 —
     * 문자열 접두어 비교가 서비스와 어댑터에 흩어지면 한쪽만 고쳐진다.
     */
    public boolean isPageUrl() {
        return urlPattern.startsWith(PAGE_URL_PREFIX);
    }

    /** 접근제어 엔진이 쓰는 캐시 1건으로 투영한다. */
    public UrlRule toRule() {
        return new UrlRule(urlPattern, httpMethod, permissionCodes);
    }

    private static String requirePattern(String urlPattern) {
        if (urlPattern == null || urlPattern.isBlank()) {
            throw new IllegalArgumentException("urlPattern must not be blank");
        }
        return urlPattern.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
