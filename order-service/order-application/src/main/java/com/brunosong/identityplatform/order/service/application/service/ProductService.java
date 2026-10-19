package com.brunosong.identityplatform.order.service.application.service;

import com.brunosong.identityplatform.order.service.application.ports.in.ProductUseCase;
import com.brunosong.identityplatform.order.service.application.ports.out.ProductRepository;
import com.brunosong.identityplatform.order.service.domain.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 상품 조회 유스케이스 구현. */
@Service
@RequiredArgsConstructor
public class ProductService implements ProductUseCase {

    private final ProductRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return repository.findAll();
    }
}
