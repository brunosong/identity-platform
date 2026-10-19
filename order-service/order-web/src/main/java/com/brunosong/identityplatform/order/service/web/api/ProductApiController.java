package com.brunosong.identityplatform.order.service.web.api;

import com.brunosong.identityplatform.order.service.application.ports.in.ProductUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 상품 목록 API. <b>로그인 없이 열린다</b>({@code SecurityConfiguration}).
 *
 * <p>상품은 누구에게나 같은 값이라 신원을 물을 이유가 없다. 신원이 필요해지는 곳은 주문부터다.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductApiController {

    private final ProductUseCase products;

    @GetMapping
    public List<ProductResponse> products() {
        return products.findAll().stream().map(ProductResponse::of).toList();
    }
}
