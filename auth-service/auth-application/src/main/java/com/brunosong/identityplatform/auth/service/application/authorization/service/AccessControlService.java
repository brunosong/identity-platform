package com.brunosong.identityplatform.auth.service.application.authorization.service;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.CheckAccessUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ListSubjectPermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ListSubjectRolesUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ReloadAccessRulesUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.SubjectRoleQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.UrlAccessQuery;
import com.brunosong.identityplatform.auth.service.domain.authorization.UrlRule;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 접근제어 엔진. realm 별 URL 접근 규칙을 캐시하고 (URL, 메서드) 매칭으로 인가를 판정한다.
 *
 * <p>한 프로세스가 두 realm 을 모두 시행하므로 캐시를 realm 별로 분리해 지연 로딩한다 — 한 realm 의
 * 규칙 변경이 다른 realm 의 캐시를 건드리지 않는다. 규칙이 바뀌면 {@link #reload(Realm)} 로 즉시
 * 갱신하고, 그와 별개로 TTL 이 지나면 다시 읽는다.
 *
 * <p><b>TTL 이 필요한 이유:</b> reload 는 그 호출을 받은 프로세스의 캐시만 갱신한다. 인스턴스가 둘
 * 이상이면 나머지는 규칙이 바뀐 줄 모른다. TTL 은 그 창을 유한하게 만든다(기본 60초). 즉시 전파가
 * 필요해지면 이 자리를 분산 캐시/브로커 무효화로 바꾼다.
 *
 * <p><b>두 realm 모두 fail-closed(화이트리스트)다.</b> 매칭되는 규칙이 없으면 보호 경로는 거부한다 —
 * 등록된 것만 통과한다. realm 마다 다르지 않으므로 그 정책은 {@code Realm} 에 값으로 두지 않았다.
 *
 * <p>보호 경로 접두어는 프로퍼티로 받는다. 어떤 경로가 보호 대상인지는 이 규칙을 시행하는 쪽의
 * URL 설계이지 접근제어 엔진이 알 일이 아니다.
 *
 * <p>읽기 포트({@link UrlAccessQuery}/{@link SubjectRoleQuery})만 쓴다 — 판정은 상태를 바꾸지 않는다.
 */
@Service
@Slf4j
public class AccessControlService implements CheckAccessUseCase, ListSubjectPermissionsUseCase,
        ListSubjectRolesUseCase, ReloadAccessRulesUseCase {

    private final UrlAccessQuery urlAccessQuery;
    private final SubjectRoleQuery subjectRoleQuery;
    private final Duration cacheTtl;
    private final List<String> protectedPathPrefixes;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** realm 별 URL 접근 규칙 캐시. */
    private final Map<Realm, CachedRules> cache = new ConcurrentHashMap<>();

    public AccessControlService(
            UrlAccessQuery urlAccessQuery,
            SubjectRoleQuery subjectRoleQuery,
            @Value("${authorization.access-control.cache-ttl-seconds:60}") long cacheTtlSeconds,
            @Value("${authorization.access-control.protected-path-prefixes:/page/,/api/}")
            List<String> protectedPathPrefixes) {
        this.urlAccessQuery = urlAccessQuery;
        this.subjectRoleQuery = subjectRoleQuery;
        this.cacheTtl = Duration.ofSeconds(Math.max(0, cacheTtlSeconds));
        this.protectedPathPrefixes = List.copyOf(protectedPathPrefixes);
    }

    @Override
    public boolean hasAccess(Realm realm, String requestUrl, String httpMethod, Set<String> userPermissions) {
        boolean matched = false;
        for (UrlRule rule : rulesOf(realm)) {
            if (!pathMatcher.match(rule.urlPattern(), requestUrl) || !rule.matchesMethod(httpMethod)) {
                continue;
            }
            matched = true;
            if (rule.allowsAny(userPermissions)) {
                return true;
            }
        }

        // 매칭 규칙이 있었는데 권한이 없으면 거부(realm 공통).
        if (matched) {
            return false;
        }

        // 매칭 규칙이 없는 경우: 두 realm 모두 fail-closed(화이트리스트)다.
        // 보호 경로는 거부하고, 그 밖(정적 리소스 등)은 통과시킨다.
        //
        // 전에는 포털만 fail-open 이었다. 그러면 규칙을 등록하지 않은 고객 API 가 조용히 열린 채로
        // 남는다 — 잊었을 때의 결과가 "막힌다" 여야지 "열린다" 여서는 안 된다.
        if (isProtectedPath(requestUrl)) {
            log.warn("RBAC 규칙 미등록 보호 경로 접근 거부: realm={}, url={}, method={}", realm, requestUrl, httpMethod);
            return false;
        }
        return true;
    }

    @Override
    public List<String> of(Realm realm, String subjectId) {
        return subjectRoleQuery.permissionCodes(realm, subjectId);
    }

    /**
     * 토큰에 실을 역할을 realm 공통과 서비스별로 갈라 읽는다.
     *
     * <p>{@code systemId} 는 토큰의 aud 다. 그 시스템에 속한 서비스들의 권한만 읽는다.
     * 전부 읽으면 토큰이 realm 전체 크기로 자라고 다른 시스템의 권한까지 실려 나간다.
     */
    @Override
    public SubjectRoles of(Realm realm, String subjectId, String systemId) {
        return new SubjectRoles(
                subjectRoleQuery.realmRoleCodes(realm, subjectId),
                subjectRoleQuery.servicePermissionCodes(realm, subjectId, systemId));
    }

    @Override
    public void reload(Realm realm) {
        List<UrlRule> rules = urlAccessQuery.activeRules(realm);
        cache.put(realm, new CachedRules(rules, Instant.now()));
        log.info("URL 접근 규칙 로딩 완료: realm={}, {}건 (패턴·메서드 기준)", realm, rules.size());
    }

    /**
     * 캐시된 규칙을 준다. 만료됐거나 없으면 읽어서 채운다.
     *
     * <p>적재를 맵 밖에서 한다. {@code computeIfAbsent} 안에서 DB 를 읽으면 그 조회가 끝날 때까지
     * 같은 버킷의 다른 연산이 막힌다 — 요청마다 지나가는 경로에서 할 일이 아니다. 동시에 두 스레드가
     * 같은 realm 을 읽어 한 번 더 조회할 수는 있지만, 읽기라 결과가 같고 곧 한쪽으로 수렴한다.
     */
    private List<UrlRule> rulesOf(Realm realm) {
        CachedRules cached = cache.get(realm);
        if (cached != null && !cached.isExpired(cacheTtl)) {
            return cached.rules();
        }
        List<UrlRule> rules = urlAccessQuery.activeRules(realm);
        cache.put(realm, new CachedRules(rules, Instant.now()));
        return rules;
    }

    /** RBAC 권한 매핑이 반드시 존재해야 하는 보호 경로인지 판정. */
    private boolean isProtectedPath(String requestUrl) {
        return protectedPathPrefixes.stream().anyMatch(requestUrl::startsWith);
    }

    private record CachedRules(List<UrlRule> rules, Instant loadedAt) {

        boolean isExpired(Duration ttl) {
            return !ttl.isZero() && loadedAt.plus(ttl).isBefore(Instant.now());
        }
    }
}
