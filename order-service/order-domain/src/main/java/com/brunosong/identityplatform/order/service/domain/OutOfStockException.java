package com.brunosong.identityplatform.order.service.domain;

/** 남은 수량보다 많이 주문했다. 품절도 여기 든다. */
public class OutOfStockException extends RuntimeException {

    public OutOfStockException(String productName) {
        super("재고가 모자랍니다: " + productName);
    }
}
