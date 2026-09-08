package com.brunosong.identityplatform.customer.service.web.support;

/** 호출자는 식별했지만 권한이 없다. 403 으로 옮긴다. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
