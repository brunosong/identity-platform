package com.brunosong.identityplatform.auth.service.application.identity.event;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.time.Instant;

/**
 * 인증(로그인) 성공 시 발행되는 애플리케이션 이벤트. auth 는 누가 소비하는지 모른 채 "주체가 인증됐다"만
 * 알린다(BC 디커플). 예를 들어 customer 가 이를 소비해 최종접속일시를 갱신한다.
 *
 * <p>{@code subjectId} 는 주체 식별자(CUSTOMER=customerUuid, EMPLOYEE=esntlId)이고 {@code realm} 로
 * 구분한다. 소비 측이 자기 관심 주체만 필터링한다. {@code authenticatedAt} 은 auth 가 확정한 인증 시각(source of truth)이다.
 */
public record PrincipalAuthenticatedEvent(String subjectId, Realm realm, Instant authenticatedAt) {
}
