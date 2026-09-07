package com.brunosong.identityplatform.auth.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * auth-service 실행 진입점.
 *
 * <p>패키지를 {@code ...auth.service} 에 둔다. 레이어 모듈(domain/application/dataaccess/web/messaging)이
 * 모두 이 아래에 있으므로 컴포넌트 스캔·엔티티 스캔·리포지토리 스캔이 기본값으로 전부 잡힌다.
 * 스캔 범위를 애노테이션 속성으로 넓히는 대신 패키지 위치로 푼다 — 모듈이 늘어도 손댈 곳이 없다.
 *
 * <p>이 서비스는 두 realm(EMPLOYEE/CUSTOMER)을 한 프로세스에서 담당한다. realm 은 설정이 아니라
 * 요청 경로가 지목한다({@code /api/auth/realms/{realm}/...}). 자세한 근거는 {@code Realm} 참고.
 */
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
