package com.brunosong.identityplatform.auth.service.application.authorization.service;

import com.brunosong.identityplatform.auth.service.application.authorization.exception.AuthorizationNotFoundException;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.DeleteUrlAccessUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.DescribeUrlAccessUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.FindUrlAccessUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.RegisterUrlAccessUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ReloadAccessRulesUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.SyncPermissionUrlAccessUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.DescribeUrlAccessCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.RegisterUrlAccessCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.UrlResourceRef;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.PermissionQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.PermissionRepository;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.UrlAccessQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.UrlAccessSearchQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.UrlAccessRepository;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageResult;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PermissionUrlAccessResult;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PermissionView;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.UrlAccessView;
import com.brunosong.identityplatform.auth.service.domain.authorization.UrlAccess;
import com.brunosong.identityplatform.auth.service.domain.authorization.valueobject.HttpMethodPattern;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * URL 접근 규칙에 관한 유스케이스 구현. 쓰기는 반영 즉시 realm 캐시를 갱신한다.
 *
 * <p>쓰기는 {@link UrlAccessRepository}(애그리거트)로, 읽기는 {@link UrlAccessQuery}(읽기 모델)로 간다.
 * 조건으로 거르는 조회는 {@link UrlAccessSearchQuery} 로 간다.
 * 매핑 동기화는 지금 붙어 있는 것을 알아야 지울 것과 더할 것을 가르므로 쓰기 포트에서 읽는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UrlAccessService implements RegisterUrlAccessUseCase, DescribeUrlAccessUseCase,
        DeleteUrlAccessUseCase, SyncPermissionUrlAccessUseCase, FindUrlAccessUseCase {

    private final UrlAccessRepository urlAccessRepository;
    private final UrlAccessQuery urlAccessQuery;
    private final UrlAccessSearchQuery urlAccessSearchQuery;
    private final PermissionRepository permissionRepository;
    private final PermissionQuery permissionQuery;
    private final ReloadAccessRulesUseCase reloadAccessRules;

    @Override
    @Transactional
    public UrlAccessView register(RegisterUrlAccessCommand command) {
        UrlAccess urlAccess = UrlAccess.create(command.realm(), command.urlPattern(), command.httpMethod(),
                command.description(), command.sortOrder());
        UrlAccess saved = urlAccessRepository.saveBasics(urlAccess);
        reloadAccessRules.reload(command.realm());
        return UrlAccessView.from(saved);
    }

    /** 저장본을 읽어 설명/정렬만 바꾼다 — 리소스 좌표는 도메인이 불변으로 잡고 있다. */
    @Override
    @Transactional
    public UrlAccessView describe(DescribeUrlAccessCommand command) {
        UrlAccess urlAccess = urlAccessRepository.findById(command.urlAccessId())
                .orElseThrow(() -> AuthorizationNotFoundException.urlAccess(command.urlAccessId()));
        urlAccess.describeAs(command.description(), command.sortOrder());
        UrlAccess saved = urlAccessRepository.saveBasics(urlAccess);
        reloadAccessRules.reload(saved.getRealm());
        return UrlAccessView.from(saved);
    }

    @Override
    @Transactional
    public void delete(Realm realm, Long urlAccessId) {
        urlAccessRepository.deleteById(urlAccessId);
        reloadAccessRules.reload(realm);
    }

    /**
     * 권한에 매핑된 URL 을 입력 목록과 맞춘다((패턴, 메서드) 기준).
     *
     * <ul>
     *   <li>해제: 목록에서 빠진 것은 이 권한의 매핑만 지운다. 리소스 자체는 남긴다 —
     *       다른 권한이 같은 리소스를 쓰고 있을 수 있다.</li>
     *   <li>추가: 같은 좌표의 리소스를 찾아 매핑을 붙이고, 없으면 리소스를 새로 만든 뒤 붙인다.</li>
     * </ul>
     */
    @Override
    @Transactional
    public void sync(Realm realm, Long permissionId, List<UrlResourceRef> urls) {
        permissionRepository.findById(permissionId)
                .orElseThrow(() -> AuthorizationNotFoundException.permission(permissionId));

        Map<String, UrlResourceRef> desired = new LinkedHashMap<>();
        if (urls != null) {
            for (UrlResourceRef ref : urls) {
                if (ref != null && ref.hasPattern()) {
                    desired.putIfAbsent(resourceKey(ref.urlPattern(), ref.httpMethod()), ref);
                }
            }
        }

        Set<String> existingKeys = new LinkedHashSet<>();
        for (UrlAccess urlAccess : urlAccessRepository.findByPermissionId(permissionId)) {
            String key = resourceKey(urlAccess.getUrlPattern(), urlAccess.getHttpMethod().value());
            existingKeys.add(key);
            if (!desired.containsKey(key)) {
                urlAccessRepository.removePermission(urlAccess.getUrlAccessId(), permissionId);
            }
        }

        for (Map.Entry<String, UrlResourceRef> entry : desired.entrySet()) {
            if (!existingKeys.contains(entry.getKey())) {
                urlAccessRepository.addPermission(resolveResourceId(realm, entry.getValue()), permissionId);
            }
        }

        reloadAccessRules.reload(realm);
    }

    private Long resolveResourceId(Realm realm, UrlResourceRef ref) {
        String method = HttpMethodPattern.of(ref.httpMethod()).value();
        return urlAccessRepository.findByPatternAndMethod(realm, ref.urlPattern(), method).stream()
                .findFirst()
                .map(UrlAccess::getUrlAccessId)
                .orElseGet(() -> urlAccessRepository.saveBasics(
                        UrlAccess.create(realm, ref.urlPattern(), method, "권한 매핑", 0)).getUrlAccessId());
    }

    private static String resourceKey(String urlPattern, String httpMethod) {
        return urlPattern + "|" + HttpMethodPattern.of(httpMethod).value();
    }

    @Override
    public List<UrlAccessView> of(Realm realm) {
        return urlAccessQuery.listActive(realm);
    }

    @Override
    public PageResult<UrlAccessView> page(Realm realm, String keyword, String category, PageQuery pageQuery) {
        return urlAccessSearchQuery.searchPage(realm, keyword, category, pageQuery);
    }

    @Override
    public Optional<UrlAccessView> byId(Long urlAccessId) {
        return urlAccessQuery.findById(urlAccessId);
    }

    @Override
    public PermissionUrlAccessResult forPermission(Long permissionId) {
        PermissionView permission = permissionQuery.findById(permissionId)
                .orElseThrow(() -> AuthorizationNotFoundException.permission(permissionId));
        return new PermissionUrlAccessResult(permission.permissionId(), permission.permissionCode(),
                permission.permissionName(), urlAccessQuery.listByPermissionId(permissionId));
    }
}
