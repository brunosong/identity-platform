package com.brunosong.identityplatform.auth.service.application.identity.ports.out.dto;

/**
 * refresh 토큰에서 읽어낸 값. 누구의 토큰인가.
 *
 * <p>한때 {@code clientId} 도 함께 들고 있었다. 재발급이 같은 audience 를 유지하게 하려는 것이었는데,
 * 지금은 {@code aud} 를 <b>경로의 realm</b> 이 정하므로 이 토큰을 쥔 쪽이 갈아끼울 방법이 없다.
 */
public record RefreshedSubject(String subjectId) {
}
