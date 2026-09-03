package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageResult;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PermissionUrlAccessResult;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.UrlAccessView;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import java.util.List;
import java.util.Optional;

/**
 * URL 접근 규칙을 찾아본다. 관리 화면의 URL 조회가 이 하나를 쓴다.
 */
public interface FindUrlAccessUseCase {

    /** 활성 URL 규칙 목록(매핑 선택 화면용). */
    List<UrlAccessView> of(Realm realm);

    /** 활성 URL 규칙 페이지(관리 화면용). */
    PageResult<UrlAccessView> page(Realm realm, String keyword, String category, PageQuery pageQuery);

    Optional<UrlAccessView> byId(Long urlAccessId);

    /** 권한 기준 역방향 — 이 권한에 매핑된 URL 리소스들. */
    PermissionUrlAccessResult forPermission(Long permissionId);
}
