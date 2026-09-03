package com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command;

/**
 * 역할의 표시 정보를 고친다. 식별자(realm/roleCode)는 대상이 아니다.
 */
public record RenameRoleCommand(Long roleId, String roleName, String description) {
}
