package com.brunosong.identityplatform.auth.service.application.authorization.readmodel;

import java.util.List;
import java.util.function.Function;

/**
 * 페이지 조회 결과. page 는 1-base.
 *
 * <p>스프링 {@code Page} 를 그대로 내보내지 않는다 — 유스케이스 반환 타입이 프레임워크에 묶이면
 * 그 프레임워크가 이 서브도메인을 부르는 모든 쪽의 의존이 된다.
 */
public record PageResult<T>(List<T> content, long totalElements, int totalPages, int page, int size) {

    public PageResult {
        content = content == null ? List.of() : List.copyOf(content);
    }

    public static <T> PageResult<T> of(List<T> content, long totalElements, int page, int size) {
        int totalPages = size <= 0 || totalElements == 0
                ? 0
                : (int) ((totalElements + size - 1) / size);
        return new PageResult<>(content, totalElements, totalPages, page, size);
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), 0L, 0, page, size);
    }

    /** 내용만 다른 타입으로 옮긴 페이지(도메인 → 뷰 변환용). */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(content.stream().map(mapper).map(r -> (R) r).toList(),
                totalElements, totalPages, page, size);
    }
}
