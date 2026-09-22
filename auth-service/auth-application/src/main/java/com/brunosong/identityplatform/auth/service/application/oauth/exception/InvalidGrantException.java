package com.brunosong.identityplatform.auth.service.application.oauth.exception;

/**
 * 토큰으로 바꿀 수 없는 인가 코드.
 *
 * <p>없는 코드, 이미 쓴 코드, 만료된 코드, 다른 앱이 들고 온 코드, PKCE 원본이 맞지 않는 코드가
 * 모두 이것 하나다. <b>사유를 나누지 않는다</b> - 나눠주면 코드를 주운 쪽이 무엇이 틀렸는지
 * 좁혀갈 수 있다. 정상적인 앱은 이 실패를 볼 일이 거의 없고, 보더라도 할 일은 로그인을 다시
 * 시작하는 것 하나뿐이라 사유가 필요 없다.
 *
 * <p>이름은 OAuth 가 정해 둔 오류 코드에서 왔다(RFC 6749 5.2의 {@code invalid_grant}).
 * 응답으로 나갈 때 그 이름이 그대로 나간다 - 받는 쪽은 표준 라이브러리일 수 있고, 그쪽은
 * 우리가 지은 이름을 모른다.
 *
 * <pre>
 * HTTP/1.1 400 Bad Request
 * { "error": "invalid_grant", "error_description": "..." }
 * </pre>
 *
 * <p>무엇이 틀렸는지는 <b>로그에 남긴다.</b> 응답에 적지 않을 뿐이지 우리가 몰라도 되는 것은
 * 아니다 - 앱이 붙는 중에 "왜 안 되지" 를 풀 수 있어야 한다.
 */
public class InvalidGrantException extends RuntimeException {

    public InvalidGrantException(String message) {
        super(message);
    }
}
