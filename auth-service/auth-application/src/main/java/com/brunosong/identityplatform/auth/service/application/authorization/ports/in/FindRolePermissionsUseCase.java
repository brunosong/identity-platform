package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RolePermissionMatrix;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RolePermissionsResult;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 역할과 권한의 매핑을 본다.
 */
public interface FindRolePermissionsUseCase {

    /** 한 역할의 권한 편집 데이터 — realm 전체 권한에 배정 여부를 얹어 준다. */
    RolePermissionsResult forRole(Long roleId);

    /** realm 의 역할×권한 매트릭스. */
    RolePermissionMatrix matrix(Realm realm);
}
