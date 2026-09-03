package com.brunosong.identityplatform.auth.service.dataaccess.support;

import java.util.Locale;

/**
 * 검색어를 LIKE 패턴으로 옮긴다. 비어 있으면 null 을 준다 — 쿼리는 null 을 "조건 없음"으로 읽는다.
 *
 * <p>소문자 변환을 여기서 한 번만 한다. 쿼리는 {@code LOWER(컬럼) LIKE :keyword} 로 비교하므로
 * 패턴 쪽도 반드시 소문자여야 하는데, 그 짝을 호출부마다 기억하게 두면 한 곳이 빠진다.
 */
public final class SearchKeyword {

    private SearchKeyword() {
    }

    /** {@code %검색어%} 소문자 패턴. 검색어가 비면 null. */
    public static String contains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
