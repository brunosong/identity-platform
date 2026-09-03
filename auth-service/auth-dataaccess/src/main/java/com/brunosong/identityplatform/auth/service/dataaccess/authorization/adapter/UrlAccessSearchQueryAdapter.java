package com.brunosong.identityplatform.auth.service.dataaccess.authorization.adapter;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.UrlAccessSearchQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageResult;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.UrlAccessView;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository.AuthzPermissionJpaRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository.AuthzUrlAccessJpaRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.support.SearchKeyword;
import com.brunosong.identityplatform.auth.service.domain.authorization.UrlAccess;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * URL 접근 규칙 검색 어댑터 — 지금은 쓰기와 같은 테이블을 읽지만, 이 클래스만 바꾸면 색인이나
 * 조회 전용 모델로 옮길 수 있다.
 *
 * <p>페이지는 목록 한 번, 건수 한 번으로 만든다. 목록 쿼리에 건수를 딸려 보내면 필터가 없을 때도
 * 매번 전체를 세게 되어, 첫 페이지만 보는 화면에서 값을 못 뽑는다. 분류표는 셀 때가 아니라
 * 내용을 만들 때만 읽는다.
 */
@Component
@RequiredArgsConstructor
public class UrlAccessSearchQueryAdapter implements UrlAccessSearchQuery {

    private static final boolean ACTIVE = true;
    private static final String PAGE_PATTERN_PREFIX = UrlAccess.PAGE_URL_PREFIX + "%";
    private static final Sort DEFAULT_SORT =
            Sort.by("urlPattern").ascending().and(Sort.by("httpMethod").ascending());

    private final AuthzUrlAccessJpaRepository urlAccessRepository;
    private final AuthzPermissionJpaRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResult<UrlAccessView> searchPage(Realm realm, String keyword, String category, PageQuery pageQuery) {
        String pattern = SearchKeyword.contains(keyword);
        String normalized = (category == null || category.isBlank()) ? null : category;
        boolean pageCategory = UrlAccess.PAGE_CATEGORY.equals(category);

        long total = urlAccessRepository.countSearch(
                realm, pattern, normalized, pageCategory, PAGE_PATTERN_PREFIX, ACTIVE);
        if (total == 0) {
            return PageResult.empty(pageQuery.page(), pageQuery.size());
        }
        Map<String, String> categories =
                UrlAccessViewMapper.categoryByPermissionCode(permissionRepository, realm);
        List<UrlAccessView> content = urlAccessRepository.searchPage(
                        realm, pattern, normalized, pageCategory, PAGE_PATTERN_PREFIX, ACTIVE,
                        PageRequest.of(pageQuery.zeroBasedPage(), pageQuery.size(), DEFAULT_SORT))
                .stream().map(entity -> UrlAccessViewMapper.toView(entity, categories)).toList();
        return PageResult.of(content, total, pageQuery.page(), pageQuery.size());
    }
}
