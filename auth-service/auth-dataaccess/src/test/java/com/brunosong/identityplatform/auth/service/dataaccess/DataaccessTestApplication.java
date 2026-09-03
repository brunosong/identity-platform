package com.brunosong.identityplatform.auth.service.dataaccess;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * dataaccess 는 부팅 가능한 앱이 아니라 @DataJpaTest 가 찾을 @SpringBootConfiguration 이 없다.
 * 테스트 전용으로 하나 둔다. authz / identity 엔티티는 감사 시각을 @PrePersist 로 직접 채워
 * @EnableJpaAuditing 이 필요 없다.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class DataaccessTestApplication {
}
