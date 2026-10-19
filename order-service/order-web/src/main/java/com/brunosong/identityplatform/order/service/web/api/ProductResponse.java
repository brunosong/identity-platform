package com.brunosong.identityplatform.order.service.web.api;

import com.brunosong.identityplatform.order.service.domain.Product;

import java.math.BigDecimal;

/** 상품 응답. 화면이 가격과 남은 수량을 서버 값으로 보이게 한다. */
public record ProductResponse(
        String productId,
        String name,
        BigDecimal price,
        int stock
) {

    public static ProductResponse of(Product product) {
        return new ProductResponse(product.getProductId(), product.getName(), product.getPrice(),
                product.getStock());
    }
}
