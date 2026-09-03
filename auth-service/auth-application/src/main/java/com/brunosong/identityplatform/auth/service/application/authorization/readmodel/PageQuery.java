package com.brunosong.identityplatform.auth.service.application.authorization.readmodel;

/**
 * 페이지 요청. page 는 1-base 다(화면 표기와 같게).
 *
 * <p>범위를 생성자에서 한 번만 보정한다 — 호출자마다 0 페이지나 10만 건을 막는 코드가 흩어지지 않게.
 */
public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 20;

    public PageQuery {
        page = Math.max(1, page);
        size = size <= 0 ? DEFAULT_SIZE : Math.min(MAX_SIZE, size);
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size);
    }

    /** 0-base 로 환산한 페이지 번호 — 저장소 어댑터용. */
    public int zeroBasedPage() {
        return page - 1;
    }
}
