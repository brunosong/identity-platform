package com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command;

/**
 * 권한의 표시 정보를 고친다. 식별자(realm/permissionCode)는 대상이 아니다.
 */
public record RenamePermissionCommand(Long permissionId, String permissionName,
                                      String category, String description) {
}
