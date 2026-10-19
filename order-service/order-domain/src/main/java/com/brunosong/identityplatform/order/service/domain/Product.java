package com.brunosong.identityplatform.order.service.domain;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 상품. 주문에 필요한 값만 갖는다(이름, 가격, 재고).
 *
 * <p>설명이나 사양처럼 화면에만 쓰는 값은 포털이 들고 있다. 주문을 판단하는 데 쓰이지 않는다.
 *
 * <p>재고를 줄이는 메서드가 여기 없다. 두 사람이 마지막 하나를 동시에 사면 읽고 줄이고 저장하는
 * 사이에 둘 다 통과한다. 그래서 줄이는 일은 "남은 수량이 충분할 때만 줄인다" 는 조건을 건 한 번의
 * 갱신으로 저장소가 한다({@code ProductRepository#takeStock}).
 */
@Getter
public class Product {

    private final String productId;
    private final String name;
    private final BigDecimal price;
    private final int stock;

    private Product(String productId, String name, BigDecimal price, int stock) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public static Product restore(String productId, String name, BigDecimal price, int stock) {
        return new Product(productId, name, price, stock);
    }
}
