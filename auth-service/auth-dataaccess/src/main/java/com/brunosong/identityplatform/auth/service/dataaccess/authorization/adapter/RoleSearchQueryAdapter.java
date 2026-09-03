package com.brunosong.identityplatform.auth.service.dataaccess.authorization.adapter;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.RoleSearchQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository.AuthzRoleJpaRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.support.SearchKeyword;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 역할 검색 어댑터 — 지금은 쓰기와 같은 테이블을 읽지만, 이 클래스만 바꾸면 색인이나
 * 조회 전용 모델로 옮길 수 있다.
 */
@Component
@RequiredArgsConstructor
public class RoleSearchQueryAdapter implements RoleSearchQuery {

    private static final boolean ACTIVE = true;

    private final AuthzRoleJpaRepository roleRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RoleView> search(Realm realm, String keyword) {
        return roleRepository.search(realm, SearchKeyword.contains(keyword), ACTIVE).stream()
                .map(RoleViewMapper::toView).toList();
    }
}
