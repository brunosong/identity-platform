package com.brunosong.identityplatform.auth.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterEmployeeAccountUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterWithPasswordUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RequestRegistrationOtpUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterEmployeeAccountCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithPasswordCommand;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리 API 체인이 realm 과 권한으로 호출자를 가르는지 본다.
 *
 * <p>체인의 검증기는 ADMIN 공개키 하나만 안다. 그래서 다른 realm 의 토큰은 서명에서 떨어지고(401),
 * ADMIN 토큰이라도 {@code AUTHZ_MANAGE} 가 없으면 들어오지 못한다(403).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class AdminApiSecurityIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final String ROLES = "/api/admin/rbac/roles?realm=ADMIN";

    @LocalServerPort
    private int port;

    @Autowired
    private RegisterEmployeeAccountUseCase registerEmployeeAccount;

    @Autowired
    private RequestRegistrationOtpUseCase requestRegistrationOtp;

    @Autowired
    private RegisterWithPasswordUseCase registerWithPassword;

    private RestClient http;
    private CodeFlowLogin codeFlow;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
        codeFlow = new CodeFlowLogin(port);
    }

    @Test
    @DisplayName("ADMIN 토큰이라도 AUTHZ_MANAGE 가 없으면 403 이다")
    void employeeWithoutPermissionIsForbidden() {
        // 역할 없이 등록된 직원. 로그인은 되지만 권한이 하나도 없다.
        registerEmployeeAccount.register(new RegisterEmployeeAccountCommand(
                "E-NOROLE", "역할 없음", "norole@example.com", null, null, null, null));
        String token = codeFlow.adminWithOtp("norole@example.com").accessToken();

        ResponseEntity<String> response = get(ROLES, token);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).contains("AUTHZ_MANAGE");
    }

    @Test
    @DisplayName("포털 토큰은 관리 API 의 검증기가 모르는 키라 서명에서 떨어진다")
    void portalTokenIsRejected() {
        requestRegistrationOtp.request(Realm.PORTAL, "customer@example.com");   // local 은 고정코드 123456
        registerWithPassword.register(new RegisterWithPasswordCommand(
                Realm.PORTAL, "customer@example.com", "고객", null, "customer@example.com", "pw12345678", "123456"));
        String token = codeFlow.portalWithPassword("customer@example.com", "pw12345678").accessToken();

        assertThat(get(ROLES, token).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("refresh 토큰을 Bearer 로 내도 401 이다. 같은 키로 서명돼 있지만 종류가 다르다")
    void refreshTokenIsNotAccepted() {
        String refresh = codeFlow.adminWithOtp("admin@example.com").refreshToken();

        assertThat(get(ROLES, refresh).getStatusCode().value()).isEqualTo(401);
    }

    private ResponseEntity<String> get(String path, String token) {
        return http.get().uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().toEntity(String.class);
    }
}
