package com.brunosong.identityplatform.auth.service.domain.authorization.valueobject;

import java.util.Locale;

/**
 * URL 접근 규칙이 대상으로 삼는 HTTP 메서드. {@code ALL} 은 모든 메서드를 뜻한다.
 *
 * <p>문자열로 다루던 것을 값 객체로 세운 이유는 비교 규칙이 한 군데여야 하기 때문이다.
 * "ALL 이면 무조건 통과, 아니면 대소문자 무시 비교"가 저장·판정·표시 세 곳에 흩어져 있으면
 * 한 곳만 고쳐지고 나머지가 남는다.
 */
public record HttpMethodPattern(String value) {

    private static final String ALL_VALUE = "ALL";

    public static final HttpMethodPattern ALL = new HttpMethodPattern(ALL_VALUE);

    public HttpMethodPattern {
        value = (value == null || value.isBlank())
                ? ALL_VALUE
                : value.trim().toUpperCase(Locale.ROOT);
    }

    /** null/빈 값은 {@link #ALL} 로 읽는다 — 메서드 무지정은 "전체"라는 뜻이다. */
    public static HttpMethodPattern of(String value) {
        return new HttpMethodPattern(value);
    }

    public boolean isAll() {
        return ALL_VALUE.equals(value);
    }

    public boolean matches(String requestMethod) {
        return isAll() || (requestMethod != null && value.equalsIgnoreCase(requestMethod.trim()));
    }

    @Override
    public String toString() {
        return value;
    }
}
