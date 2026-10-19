package com.brunosong.identityplatform.order.service.application.ports.in;

import com.brunosong.identityplatform.order.service.domain.Product;

import java.util.List;

/** 상품 조회. 누구에게나 같은 값이라 사람을 받지 않는다. */
public interface ProductUseCase {

    List<Product> findAll();
}
