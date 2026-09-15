package com.brunosong.identityplatform.order.service.web.support;

/** 실패 응답 본문. 상태코드는 HTTP 가 나르고 본문은 사람이 읽을 사유만 담는다. */
public record ApiError(String message) {
}
