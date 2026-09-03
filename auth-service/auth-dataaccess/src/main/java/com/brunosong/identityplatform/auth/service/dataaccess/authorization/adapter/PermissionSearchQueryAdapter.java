package com.brunosong.identityplatform.auth.service.dataaccess.authorization.adapter;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.PermissionSearchQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageResult;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PermissionView;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository.AuthzPermissionJpaRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.support.SearchKeyword;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 권한 검색 어댑터 — 지금은 쓰기와 같은 테이블을 읽지만, 이 클래스만 바꾸면 색인이나
 * 조회 전용 모델로 옮길 수 있다.
 *
 * <p>페이지는 목록 한 번, 건수 한 번으로 만든다. 목록 쿼리에 건수를 딸려 보내면 필터가 없을 때도
 * 매번 전체를 세게 되어, 첫 페이지만 보는 화면에서 값을 못 뽑는다.
 */
@Component
@RequiredArgsConstructor
public class PermissionSearchQueryAdapter implements PermissionSearchQuery {

    private static final boolean ACTIVE = true;
    private static final Sort DEFAULT_SORT =
            Sort.by("category").ascending().and(Sort.by("permissionCode").ascending());

    private final AuthzPermissionJpaRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PermissionView> search(Realm realm, String keyword) {
        return permissionRepository.search(realm, SearchKeyword.contains(keyword), ACTIVE).stream()
                .map(PermissionViewMapper::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<PermissionView> searchPage(Realm realm, String keyword, PageQuery pageQuery) {
        String pattern = SearchKeyword.contains(keyword);
        long total = permissionRepository.countSearch(realm, pattern, ACTIVE);
        if (total == 0) {
            return PageResult.empty(pageQuery.page(), pageQuery.size());
        }
        List<PermissionView> content = permissionRepository.searchPage(realm, pattern, ACTIVE,
                        PageRequest.of(pageQuery.zeroBasedPage(), pageQuery.size(), DEFAULT_SORT))
                .stream().map(PermissionViewMapper::toView).toList();
        return PageResult.of(content, total, pageQuery.page(), pageQuery.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listActiveCategories(Realm realm) {
        return permissionRepository.findActiveCategories(realm, ACTIVE);
    }
}
