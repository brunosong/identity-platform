package com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 보호할 URL 리소스를 등록한다. 좌표(realm/패턴/메서드)는 등록 뒤 바꾸지 않는다 —
 * 좌표가 바뀌면 붙어 있던 권한 매핑이 조용히 다른 URL 로 옮겨간다.
 */
public record RegisterUrlAccessCommand(Realm realm, String urlPattern, String httpMethod,
                                       String description, int sortOrder) {
}
