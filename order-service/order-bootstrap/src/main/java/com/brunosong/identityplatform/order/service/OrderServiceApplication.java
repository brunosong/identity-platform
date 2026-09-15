package com.brunosong.identityplatform.order.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * order-service 실행 진입점.
 *
 * <p>포털 realm 의 <b>두 번째</b> 소비 서비스다. customer-service 와 같은 발급자를 보고, 같은
 * 로그인으로 받은 같은 토큰을 받는다. 다른 것은 요구하는 {@code aud} 와 역할뿐이다.
 *
 * <p>API 만 제공한다. 로그인 화면도, 세션도, 자격증명도 없다.
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
