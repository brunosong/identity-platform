package com.brunosong.identityplatform.auth.service.application.authorization.readmodel;

import java.util.List;

/** 권한 기준 역방향 조회 — 이 권한에 매핑된 URL 리소스들. */
public record PermissionUrlAccessResult(Long permissionId, String permissionCode, String permissionName,
                                        List<UrlResourceView> urls) {

    public PermissionUrlAccessResult {
        urls = urls == null ? List.of() : List.copyOf(urls);
    }
}
