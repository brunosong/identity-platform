package com.brunosong.identityplatform.auth.service.web.support;

/** 그런 경로/자원이 없다. 404 로 옮긴다. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
