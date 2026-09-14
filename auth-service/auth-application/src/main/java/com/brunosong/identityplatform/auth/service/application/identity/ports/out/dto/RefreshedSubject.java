package com.brunosong.identityplatform.auth.service.application.identity.ports.out.dto;

/**
 * refresh 토큰에서 읽어낸 값 — 누구의 토큰이고, 어느 클라이언트가 받아 갔는가.
 *
 * <p>{@code clientId} 가 필요한 이유는 <b>재발급이 같은 audience 를 유지해야</b> 하기 때문이다.
 * 요청에서 다시 받으면 refresh 토큰을 쥔 쪽이 audience 를 갈아끼울 수 있다 — 고객 포털 토큰으로
 * 어드민 서비스용 audience 를 받아내는 길이 열린다. 그래서 발급 시점의 클라이언트를 토큰이 기억한다.
 */
public record RefreshedSubject(String subjectId, String clientId) {
}
