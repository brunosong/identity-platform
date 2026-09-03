package com.brunosong.identityplatform.auth.service.application.identity.ports.out.dto;

/**
 * provider 가 검증한 외부 신원. email 은 provider 가 소유를 검증한 값이어야 한다 — 자동 계정 연결의 키다
 * (미검증/미제공이면 자동 연결하지 않는다).
 */
public record VerifiedSocialIdentity(String providerUid, String email, String name) {
}
