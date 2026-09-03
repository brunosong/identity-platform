package com.brunosong.identityplatform.auth.service.application.authorization.exception;

/**
 * 인가 관리에서 대상(역할/권한/URL 규칙)을 찾지 못했을 때. 호출자가 404 로 옮길 수 있게
 * 입력값 오류({@link IllegalArgumentException})와 구분한다.
 *
 * <p>유스케이스(port.in)와 저장소 어댑터(port.out 구현) 양쪽에서 던지고 웹 어댑터가 잡는다.
 * 어느 한쪽 포트 패키지에 넣으면 반대쪽이 그 패키지를 import 하게 되므로 서브도메인 루트에 둔다.
 */
public class AuthorizationNotFoundException extends RuntimeException {

    public AuthorizationNotFoundException(String message) {
        super(message);
    }

    public static AuthorizationNotFoundException role(Long roleId) {
        return new AuthorizationNotFoundException("역할을 찾을 수 없습니다: " + roleId);
    }

    public static AuthorizationNotFoundException permission(Long permissionId) {
        return new AuthorizationNotFoundException("권한을 찾을 수 없습니다: " + permissionId);
    }

    public static AuthorizationNotFoundException urlAccess(Long urlAccessId) {
        return new AuthorizationNotFoundException("URL 접근 규칙을 찾을 수 없습니다: " + urlAccessId);
    }
}
