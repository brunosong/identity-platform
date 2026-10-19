package com.brunosong.identityplatform.order.service.domain;

/** 없는 상품을 주문했다. */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String productId) {
        super("없는 상품입니다: " + productId);
    }
}
