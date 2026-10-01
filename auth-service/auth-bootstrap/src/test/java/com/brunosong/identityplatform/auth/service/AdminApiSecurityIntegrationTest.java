package com.brunosong.identityplatform.auth.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterWithPasswordUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RequestRegistrationOtpUseCase;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
 * <p>auth 를 관리하는 것은 MASTER realm 이다. 체인의 검증기는 MASTER 공개키 하나만 알아서 직원(ADMIN)이나
 * 고객(PORTAL) 토큰은 서명에서 떨어지고(401), MASTER 토큰이라도 {@code AUTHZ_MANAGE} 가 없으면 403 이다.
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

    /** V9003 시드의 관리자 비밀번호 "admin" 의 해시. 역할 없는 MASTER 계정을 심을 때 같은 값을 쓴다. */
    private static final String ADMIN_PASSWORD_HASH = "$2a$10$bCHrPCXbt4RfTFiG9ZLOqeV.YH/BUb4BBU7trmQxgcoCpO385VAXi";

    @LocalServerPort
    private int port;

    @Autowired
    private RequestRegistrationOtpUseCase requestRegistrationOtp;

    @Autowired
    private RegisterWithPasswordUseCase registerWithPassword;

    @Autowired
    private JdbcTemplate jdbc;

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
    @DisplayName("MASTER 관리자는 관리 API 를 부른다")
    void masterAdminIsAllowed() {
        String token = codeFlow.masterWithPassword("admin", "admin").accessToken();

        assertThat(get(ROLES, token).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("직원 realm 의 부트스트랩 관리자라도 관리 API 는 401 이다. auth 를 관리하는 realm 이 아니다")
    void employeeTokenIsRejected() {
        String token = codeFlow.adminWithOtp("admin@example.com").accessToken();

        assertThat(get(ROLES, token).getStatusCode().value()).isEqualTo(401);
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
    @DisplayName("MASTER 계정이라도 AUTHZ_MANAGE 가 없으면 403 이다")
    void masterWithoutPermissionIsForbidden() {
        // MASTER 계정은 만드는 API 가 없어서 시드처럼 직접 심는다. 역할은 주지 않는다.
        jdbc.update("INSERT INTO identity_principal (principal_id, subject_id, realm, status, created_at, updated_at) "
                + "VALUES ('00000000-0000-0000-0000-0000000000b2', 'master-viewer', 'MASTER', 'ACTIVE', now(), now())");
        jdbc.update("INSERT INTO identity_password_account "
                + "(password_account_id, principal_id, realm, login_id, password_hash, created_at) "
                + "VALUES ('00000000-0000-0000-0000-0000000000c2', '00000000-0000-0000-0000-0000000000b2', "
                + "'MASTER', 'viewer', ?, now())", ADMIN_PASSWORD_HASH);
        String token = codeFlow.masterWithPassword("viewer", "admin").accessToken();

        ResponseEntity<String> response = get(ROLES, token);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).contains("AUTHZ_MANAGE");
    }

    @Test
    @DisplayName("refresh 토큰을 Bearer 로 내도 401 이다. 같은 키로 서명돼 있지만 종류가 다르다")
    void refreshTokenIsNotAccepted() {
        String refresh = codeFlow.masterWithPassword("admin", "admin").refreshToken();

        assertThat(get(ROLES, refresh).getStatusCode().value()).isEqualTo(401);
    }

    private ResponseEntity<String> get(String path, String token) {
        return http.get().uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().toEntity(String.class);
    }
}
