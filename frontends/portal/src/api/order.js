import { endpoints } from './config';
import { request } from './http';

/**
 * order-service 호출.
 *
 * 상품 목록은 토큰 없이 부른다. 누구에게나 같은 값이다. 주문부터 토큰을 싣는다.
 *
 * 주문 본문에 가격이 없는 것에 주목할 것. 가격은 서버가 상품에서 읽는다. 여기서 보내면
 * 개발자 도구로 본문을 고치는 것만으로 값을 정할 수 있다.
 */

const orderUrl = () => endpoints().order;

/** 상품의 이름, 가격, 남은 수량. */
export function fetchProducts() {
    return request(orderUrl(), 'GET', '/api/products');
}

/** 주문한다. 재고가 모자라면 409, 없는 상품이면 404 다. */
export function placeOrder(accessToken, { productId, quantity }) {
    return request(orderUrl(), 'POST', '/api/orders', {
        token: accessToken,
        body: { productId, quantity },
    });
}

/** 내 주문 목록. 최근 것이 먼저다. */
export function myOrders(accessToken) {
    return request(orderUrl(), 'GET', '/api/orders', { token: accessToken });
}
