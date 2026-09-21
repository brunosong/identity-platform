package com.brunosong.identityplatform.auth.service.application.oauth.exception;

/**
 * 받아들일 수 없는 인가 요청.
 *
 * <p>이 예외가 나면 <b>돌려보내지 않는다.</b> 우리 화면에서 끝낸다 - 돌아갈 주소를 아직 믿을 수
 * 없는 상태이기 때문이다. 요청자가 적어 보낸 주소로 오류를 실어 보내는 것 자체가 배달이다.
 */
public class InvalidAuthorizationRequestException extends RuntimeException {

    public InvalidAuthorizationRequestException(String message) {
        super(message);
    }
}
