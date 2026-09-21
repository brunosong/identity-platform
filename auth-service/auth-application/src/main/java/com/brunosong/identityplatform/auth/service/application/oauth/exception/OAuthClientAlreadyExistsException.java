package com.brunosong.identityplatform.auth.service.application.oauth.exception;

/**
 * 이미 쓰이고 있는 client_id 로 등록을 시도했을 때.
 *
 * <p>덮어쓰지 않고 막는다. 같은 이름으로 저장하면 앞서 등록된 앱의 돌아갈 주소가 조용히
 * 바뀌어 버리고, 그 앱은 다음 로그인부터 갈 곳을 잃는다.
 */
public class OAuthClientAlreadyExistsException extends RuntimeException {

    public OAuthClientAlreadyExistsException(String clientId) {
        super("이미 등록된 client_id 입니다: " + clientId);
    }
}
