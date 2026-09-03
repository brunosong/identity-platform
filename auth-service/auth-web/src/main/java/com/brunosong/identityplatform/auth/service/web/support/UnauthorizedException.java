package com.brunosong.identityplatform.auth.service.web.support;

/** 호출자를 식별하지 못했다(토큰 없음/무효/만료). 401 로 옮긴다. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
