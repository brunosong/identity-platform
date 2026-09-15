package com.brunosong.identityplatform.order.service.web.api;

import com.brunosong.identityplatform.order.service.application.ports.in.OrderUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 고객 본인의 주문 API.
 *
 * <h2>customer-service 와 다른 점</h2>
 * {@code /api/customers/me} 는 조회 키가 요청에 <b>없었다</b>. 토큰의 {@code sub} 하나뿐이라 남의
 * 것을 가리킬 방법 자체가 없었다. 여기는 다르다. 주문은 사람당 여럿이라 주문번호를 경로로 받고,
 * 그 자리에 <b>남의 주문번호를 적어 넣을 수 있다.</b>
 *
 * <p>그래서 조회 조건에 {@code sub} 를 함께 건다. 주문번호만으로 찾는 길은 이 서비스 어디에도 없다
 * ({@code OrderRepository} 에 그런 메서드가 없다). 잊을 자리를 만들지 않는 편이 확인을 잊지 말자고
 * 다짐하는 것보다 낫다.
 *
 * <h2>남의 주문은 403 이 아니라 404 다</h2>
 * 403 은 "그 주문은 있는데 당신 것이 아니다" 를 알려준다. 주문번호를 훑으면서 403 과 404 를 세면
 * 남의 주문이 몇 건인지, 어느 번호가 살아 있는지 알아낼 수 있다. 소유자를 조건에 함께 걸면 두
 * 경우가 같은 결과가 되고, 응답은 그 둘을 구분하지 못한다.
 *
 * <p>인증 코드는 여전히 한 줄도 없다. 여기 도달했다는 것이 검증을 통과했다는 뜻이고
 * ({@code SecurityConfiguration}), 역할 검사도 필터가 끝낸다. 컨트롤러가 하는 확인은
 * <b>소유권 하나</b>다. 그것만은 토큰도 게이트웨이도 대신 답할 수 없다.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class MyOrderApiController {

    private final OrderUseCase orders;

    /** 내 주문 목록. 빈 목록과 "주문이 없는 사람" 은 같은 것이라 404 를 쓰지 않는다. */
    @GetMapping
    public List<OrderResponse> myOrders(@AuthenticationPrincipal Jwt token) {
        return orders.findMine(token.getSubject()).stream().map(OrderResponse::of).toList();
    }

    /** 내 주문 하나. 없는 주문번호든 남의 주문번호든 404 다. */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> myOrder(@AuthenticationPrincipal Jwt token,
                                                 @PathVariable String orderId) {
        return orders.findOwned(token.getSubject(), orderId)
                .map(order -> ResponseEntity.ok(OrderResponse.of(order)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 주문한다.
     *
     * <p>주문자를 본문으로 받지 않는다. 받으면 남의 이름으로 주문할 수 있다.
     */
    @PostMapping
    public OrderResponse place(@AuthenticationPrincipal Jwt token,
                               @Valid @RequestBody PlaceOrderRequest body) {
        return OrderResponse.of(orders.place(
                token.getSubject(), body.productName(), body.quantity(), body.unitPrice()));
    }

    public record PlaceOrderRequest(
            @NotBlank @Size(max = 200) String productName,
            @Min(1) int quantity,
            @NotNull @DecimalMin("0") BigDecimal unitPrice) {
    }
}
