package com.brunosong.identityplatform.auth.service.application.authorization.readmodel;

import com.brunosong.identityplatform.auth.service.domain.authorization.UrlAccess;

/** URL 리소스 좌표만 담은 결과 — 권한↔URL 매핑 화면에서 쓴다. */
public record UrlResourceView(Long urlAccessId, String urlPattern, String httpMethod) {

    public static UrlResourceView from(UrlAccess urlAccess) {
        return new UrlResourceView(urlAccess.getUrlAccessId(), urlAccess.getUrlPattern(),
                urlAccess.getHttpMethod().value());
    }
}
