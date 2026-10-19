package com.brunosong.identityplatform.order.service.dataaccess.adapter;

import com.brunosong.identityplatform.order.service.application.ports.out.ProductRepository;
import com.brunosong.identityplatform.order.service.dataaccess.entity.ProductJpaEntity;
import com.brunosong.identityplatform.order.service.dataaccess.repository.ProductJpaRepository;
import com.brunosong.identityplatform.order.service.domain.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** {@link ProductRepository} 영속성 어댑터. */
@Component
@RequiredArgsConstructor
public class ProductPersistenceAdapter implements ProductRepository {

    private final ProductJpaRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return repository.findAllByOrderByProductIdAsc().stream()
                .map(ProductPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(String productId) {
        return repository.findById(productId).map(ProductPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public boolean takeStock(String productId, int quantity) {
        int updated = repository.takeStock(productId, quantity);
        return updated == 1;
    }

    private static Product toDomain(ProductJpaEntity e) {
        return Product.restore(e.getProductId(), e.getName(), e.getPrice(), e.getStock());
    }
}
