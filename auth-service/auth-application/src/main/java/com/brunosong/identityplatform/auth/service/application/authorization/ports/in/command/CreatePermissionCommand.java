package com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 새 권한을 만든다. realm 과 permissionCode 는 만든 뒤 바꾸지 않는다 —
 * 코드는 발급된 토큰의 권한 목록과 대응하는 값이라 바뀌면 그 대응이 끊긴다.
 *
 * <p>serviceId 가 토큰의 {@code resource_access} 칸이 된다. 비우면 서비스가 없는 권한이 되어 토큰에 실리지 않는다.
 */
public record CreatePermissionCommand(Realm realm, String serviceId, String permissionCode, String permissionName,
                                      String category, String description) {
}
