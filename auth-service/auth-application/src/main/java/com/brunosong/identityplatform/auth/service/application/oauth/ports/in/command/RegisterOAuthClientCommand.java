package com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.List;

/**
 * 앱을 새로 등록한다. client_id 와 realm 은 등록 후 바꾸지 않는다 - 둘 다 이미 발급된 주소창의
 * 요청이 물고 있는 값이라, 바꾸면 같은 앱의 수정이 아니라 다른 앱이다.
 *
 * <p>{@code confidential} 이면 시크릿을 발급한다. 시크릿을 지킬 수 있는 서버 앱만 켠다.
 * 값은 요청자가 고르지 않고 우리가 뽑는다.
 */
public record RegisterOAuthClientCommand(String clientId, Realm realm, List<String> redirectUris,
                                         boolean confidential) {
}
