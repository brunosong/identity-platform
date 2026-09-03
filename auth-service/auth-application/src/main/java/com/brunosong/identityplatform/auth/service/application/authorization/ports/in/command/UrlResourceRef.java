package com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command;

/**
 * URL 리소스 좌표 — 권한↔URL 매핑을 편집할 때 대상 리소스를 가리킨다.
 * httpMethod 가 비면 ALL 로 읽는다.
 */
public record UrlResourceRef(String urlPattern, String httpMethod) {

    public UrlResourceRef {
        urlPattern = urlPattern == null ? null : urlPattern.trim();
    }

    public boolean hasPattern() {
        return urlPattern != null && !urlPattern.isBlank();
    }
}
