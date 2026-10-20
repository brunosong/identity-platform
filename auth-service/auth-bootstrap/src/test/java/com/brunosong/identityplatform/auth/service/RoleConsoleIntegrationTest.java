package com.brunosong.identityplatform.auth.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterWithPasswordUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RequestRegistrationOtpUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithPasswordCommand;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 화면에서 역할의 권한이나 사람의 역할을 고치면 그 사람의 다음 토큰에 그대로 실리는지 본다.
 *
 * <p>화면이 저장만 하고 끝나는지가 아니라, 고친 것이 토큰의 {@code realm_access}, {@code resource_access} 까지
 * 가는지가 이 화면들의 쓸모라 끝까지 밟는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class RoleConsoleIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

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
    private String consoleCookie;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
        codeFlow = new CodeFlowLogin(port);
        consoleCookie = "CONSOLE_ACCESS=" + codeFlow.masterWithPassword("admin", "admin").accessToken();
    }

    @Test
    @DisplayName("역할의 권한 화면이 열린다")
    void detailOpens() {
        ResponseEntity<String> page = http.get().uri("/page/roles/" + customerRoleId())
                .header(HttpHeaders.COOKIE, consoleCookie)
                .retrieve().toEntity(String.class);

        assertThat(page.getStatusCode().value()).isEqualTo(200);
        assertThat(page.getBody()).contains("customer-service", "order-service", "PROFILE_WRITE");
    }

    @Test
    @DisplayName("화면에서 체크한 권한만 다음 토큰의 resource_access 에 실린다")
    void savedPermissionsReachToken() {
        Long roleId = customerRoleId();

        ResponseEntity<Void> saved = http.post().uri("/page/roles/" + roleId + "/permissions")
                .header(HttpHeaders.COOKIE, consoleCookie)
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body("permissionIds=" + portalPermissionId("PROFILE_READ"))
                .retrieve().toBodilessEntity();

        assertThat(saved.getStatusCode().value()).isEqualTo(302);
        assertThat(saved.getHeaders().getLocation().getPath()).isEqualTo("/page/roles/" + roleId);

        String email = "console-role@example.com";
        registerCustomer(email);
        Map<String, Object> claims = payloadOf(codeFlow.portalWithPassword(email, "pw12345678").accessToken());
        assertThat(claims.get("resource_access")).isEqualTo(Map.of(
                "customer-service", Map.of("roles", List.of("PROFILE_READ"))));
    }

    @Test
    @DisplayName("이메일로 사람을 찾으면 그 사람과 realm 의 역할이 나온다. 없으면 없다고 한다")
    void findsSubjectByEmail() {
        String email = "console-find@example.com";
        registerCustomer(email);

        ResponseEntity<String> found = page("/page/subjects?realm=PORTAL&login=" + email);
        assertThat(found.getStatusCode().value()).isEqualTo(200);
        assertThat(found.getBody()).contains(email, "CUSTOMER");

        ResponseEntity<String> missing = page("/page/subjects?realm=PORTAL&login=nobody@example.com");
        assertThat(missing.getBody()).contains("인 사람이 없다");
    }

    @Test
    @DisplayName("화면에서 역할을 모두 떼면 다음 토큰에는 역할도 권한도 없다")
    void removedRolesLeaveToken() {
        String email = "console-subject@example.com";
        registerCustomer(email);
        String subjectId = (String) payloadOf(codeFlow.portalWithPassword(email, "pw12345678").accessToken()).get("sub");

        ResponseEntity<Void> saved = http.post().uri("/page/subjects/roles")
                .header(HttpHeaders.COOKIE, consoleCookie)
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body("realm=PORTAL&subjectId=" + subjectId + "&login=" + email)
                .retrieve().toBodilessEntity();

        assertThat(saved.getStatusCode().value()).isEqualTo(302);
        assertThat(saved.getHeaders().getLocation().getPath()).isEqualTo("/page/subjects");

        Map<String, Object> claims = payloadOf(codeFlow.portalWithPassword(email, "pw12345678").accessToken());
        assertThat(claims.get("realm_access")).isEqualTo(Map.of("roles", List.of()));
        assertThat(claims.get("resource_access")).isEqualTo(Map.of());
    }

    private ResponseEntity<String> page(String path) {
        return http.get().uri(path)
                .header(HttpHeaders.COOKIE, consoleCookie)
                .retrieve().toEntity(String.class);
    }

    private Long customerRoleId() {
        return jdbc.queryForObject(
                "SELECT role_id FROM authz_role WHERE realm = 'PORTAL' AND role_code = 'CUSTOMER'", Long.class);
    }

    private Long portalPermissionId(String permissionCode) {
        return jdbc.queryForObject(
                "SELECT permission_id FROM authz_permission WHERE realm = 'PORTAL' AND permission_code = ?",
                Long.class, permissionCode);
    }

    private void registerCustomer(String email) {
        // local 은 고정코드 123456
        requestRegistrationOtp.request(Realm.PORTAL, email);
        registerWithPassword.register(new RegisterWithPasswordCommand(
                Realm.PORTAL, email, "Tester", null, email, "pw12345678", "123456"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payloadOf(String jwt) {
        byte[] json = Base64.getUrlDecoder().decode(jwt.split("\\.")[1]);
        try {
            return new ObjectMapper().readValue(new String(json, StandardCharsets.UTF_8), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("토큰 payload 를 읽지 못했다", e);
        }
    }
}
