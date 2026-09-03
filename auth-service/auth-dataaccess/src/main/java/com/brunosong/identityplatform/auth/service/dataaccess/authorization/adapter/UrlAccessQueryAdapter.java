package com.brunosong.identityplatform.auth.service.dataaccess.authorization.adapter;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.UrlAccessQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.UrlAccessView;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.UrlResourceView;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository.AuthzPermissionJpaRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository.AuthzUrlAccessJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.authorization.UrlRule;
import com.brunosong.identityplatform.auth.service.domain.authorization.valueobject.HttpMethodPattern;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * URL 접근 규칙 조회 어댑터 — 엔티티를 읽기 모델로 바로 투영한다.
 *
 * <p>표시용 분류는 {@link UrlAccessViewMapper} 가 채운다. 조건 검색은
 * {@link UrlAccessSearchQueryAdapter} 가 맡는다.
 */
@Component
@RequiredArgsConstructor
public class UrlAccessQueryAdapter implements UrlAccessQuery {

    private static final boolean ACTIVE = true;

    private final AuthzUrlAccessJpaRepository urlAccessRepository;
    private final AuthzPermissionJpaRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<UrlAccessView> listActive(Realm realm) {
        Map<String, String> categories =
                UrlAccessViewMapper.categoryByPermissionCode(permissionRepository, realm);
        return urlAccessRepository.findByRealmAndActiveIsTrueOrderBySortOrderAscUrlPatternAsc(realm).stream()
                .map(entity -> UrlAccessViewMapper.toView(entity, categories)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UrlAccessView> findById(Long urlAccessId) {
        return urlAccessRepository.findById(urlAccessId)
                .map(entity -> UrlAccessViewMapper.toView(entity,
                        UrlAccessViewMapper.categoryByPermissionCode(permissionRepository, entity.getRealm())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UrlResourceView> listByPermissionId(Long permissionId) {
        return urlAccessRepository.findByPermissionId(permissionId, ACTIVE).stream()
                .map(entity -> new UrlResourceView(
                        entity.getUrlAccessId(), entity.getUrlPattern(), entity.getHttpMethod()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UrlRule> activeRules(Realm realm) {
        // (패턴, 메서드)별로 권한코드를 모은 뒤에 규칙을 만든다. UrlRule 은 불변이라 만들고 나서 채울 수 없다.
        Map<String, List<String>> codesByResource = new LinkedHashMap<>();
        Map<String, String[]> resourceByKey = new LinkedHashMap<>();
        for (Object[] row : urlAccessRepository.findActiveUrlPermissionRows(realm, ACTIVE)) {
            String urlPattern = (String) row[0];
            String httpMethod = (String) row[1];
            String permissionCode = (String) row[2];
            String key = urlPattern + "|" + httpMethod;
            resourceByKey.putIfAbsent(key, new String[]{urlPattern, httpMethod});
            codesByResource.computeIfAbsent(key, k -> new ArrayList<>()).add(permissionCode);
        }

        List<UrlRule> rules = new ArrayList<>(resourceByKey.size());
        resourceByKey.forEach((key, resource) -> rules.add(new UrlRule(
                resource[0], HttpMethodPattern.of(resource[1]), Set.copyOf(codesByResource.get(key)))));
        return rules;
    }
}
