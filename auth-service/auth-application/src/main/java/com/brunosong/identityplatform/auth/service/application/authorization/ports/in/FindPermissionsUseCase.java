package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageResult;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PermissionView;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import java.util.List;
import java.util.Optional;

/**
 * 권한을 찾아본다. 관리 화면의 권한 조회가 이 하나를 쓴다.
 */
public interface FindPermissionsUseCase {

    /** 활성 권한 목록(선택 화면용). keyword 는 코드/이름/분류 부분일치이며 비면 전체다. */
    List<PermissionView> of(Realm realm, String keyword);

    /** 활성 권한 페이지(관리 화면용). */
    PageResult<PermissionView> page(Realm realm, String keyword, PageQuery pageQuery);

    Optional<PermissionView> byId(Long permissionId);

    /** 화면 필터용 분류 목록. 페이지 호출 URL 분류가 맨 앞에 붙는다. */
    List<String> categories(Realm realm);
}
