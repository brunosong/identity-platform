package com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.List;

/**
 * 앱을 새로 등록한다. client_id 와 realm 은 등록 후 바꾸지 않는다 - 둘 다 이미 발급된 주소창의
 * 요청이 물고 있는 값이라, 바꾸면 같은 앱의 수정이 아니라 다른 앱이다.
 */
public record RegisterOAuthClientCommand(String clientId, Realm realm, List<String> redirectUris) {
}
