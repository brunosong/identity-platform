package com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 새 역할을 만든다. realm 과 roleCode 는 만든 뒤 바꾸지 않는다 —
 * 코드가 바뀌면 같은 역할의 수정이 아니라 다른 역할이다.
 */
public record CreateRoleCommand(Realm realm, String roleCode, String roleName, String description) {
}
