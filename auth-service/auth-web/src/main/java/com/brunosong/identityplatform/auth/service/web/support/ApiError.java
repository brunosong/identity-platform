package com.brunosong.identityplatform.auth.service.web.support;

/**
 * 실패 응답 본문. 상태코드는 HTTP 가 나르고 본문은 사람이 읽을 사유만 담는다.
 *
 * <p>성공/실패를 본문 필드로 알리고 상태코드는 늘 200 으로 두던 방식을 버렸다. 그 방식은
 * 게이트웨이·프록시·모니터링이 실패를 볼 수 없게 만든다 — 재시도도 알림도 걸리지 않는다.
 */
public record ApiError(String message) {
}
