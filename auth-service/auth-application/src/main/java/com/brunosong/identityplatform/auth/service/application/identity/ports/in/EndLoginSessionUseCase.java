package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

/**
 * 로그인 세션을 끊는다. 로그아웃이다.
 *
 * <p>끊고 나면 다음 인가 요청부터 로그인 화면이 다시 뜬다. 한 번 로그인이 여러 앱에 걸렸던
 * 것처럼, <b>한 번 로그아웃도 여러 앱에 걸린다.</b> 앱마다 로그아웃하는 것이 아니다.
 *
 * <p>realm 을 받지 않는다. 세션 값 자체가 난수라 그것을 쥔 쪽이 임자이고, 끊는 일은 realm 을
 * 가릴 이유가 없다. 남의 세션 값을 알아내 끊는 경우를 생각할 수 있지만, 그 값을 안다는 것은
 * 이미 그것으로 로그인할 수 있다는 뜻이라 끊는 쪽이 더 큰 문제가 되지 않는다.
 *
 * <p><b>이미 발급된 토큰은 죽지 않는다.</b> access 토큰은 서명만 맞으면 통하는 무상태 값이라
 * 남은 수명 동안 살아 있다. 여기서 끊는 것은 "다음에 또 로그인시켜 줄 것인가" 하나다.
 * 발급된 토큰까지 막으려면 그 토큰을 쓸 때마다 서버에 물어보는 구조가 따로 필요하다.
 */
public interface EndLoginSessionUseCase {

    void end(String sessionId);
}
