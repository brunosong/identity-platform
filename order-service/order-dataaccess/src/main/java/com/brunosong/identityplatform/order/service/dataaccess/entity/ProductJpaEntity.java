package com.brunosong.identityplatform.order.service.dataaccess.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** products: 상품. 주문할 때 가격과 재고를 여기서 읽는다. */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class ProductJpaEntity {

    @Id
    @Column(name = "product_id", length = 50)
    private String productId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "price", nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "stock", nullable = false)
    private int stock;
}
