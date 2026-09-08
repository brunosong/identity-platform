package com.brunosong.identityplatform.customer.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * customer-service 실행 진입점.
 *
 * <p>API 만 제공한다. 로그인 화면도, 세션도, 자격증명도 없다 — 그것은 auth-service 의 몫이고
 * 이 서비스는 auth 가 서명한 토큰을 받아 확인만 한다.
 *
 * <p>auth 에 대해 아는 것은 설정 한 줄뿐이다: {@code auth.client.jwks-uri}.
 * 그 주소로 공개키를 받아다 서명을 스스로 검증하므로, 요청마다 auth 로 왕복이 생기지 않고
 * auth 가 잠시 죽어도 이미 발급된 토큰은 계속 통과한다.
 */
@SpringBootApplication
public class CustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
