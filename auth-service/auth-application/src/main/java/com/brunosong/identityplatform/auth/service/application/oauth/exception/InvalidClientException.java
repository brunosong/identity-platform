package com.brunosong.identityplatform.auth.service.application.oauth.exception;

/**
 * 토큰을 받으러 온 앱이 자기가 그 앱임을 증명하지 못했다.
 *
 * <p>시크릿이 있는 앱이 시크릿을 안 냈거나 틀리게 냈을 때, 시크릿이 없는 앱이 시크릿을 냈을 때다.
 * {@link InvalidGrantException} 과 나눈 것은 명세가 나눠 두었기 때문이다(RFC 6749 5.2의
 * {@code invalid_client}). 저쪽은 "이 코드로는 안 된다" 이고, 이쪽은 "당신이 누구인지 모르겠다" 다.
 * 앱을 붙이는 사람에게는 코드를 다시 받을 일인지 설정을 고칠 일인지가 여기서 갈린다.
 *
 * <p>사유는 여기서도 로그에만 남긴다.
 */
public class InvalidClientException extends RuntimeException {

    public InvalidClientException(String message) {
        super(message);
    }
}
