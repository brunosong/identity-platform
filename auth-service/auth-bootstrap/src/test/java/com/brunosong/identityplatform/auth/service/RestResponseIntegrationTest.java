package com.brunosong.identityplatform.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 응답이 HTTP 와 OAuth 명세가 정한 모양인지 본다. 상태코드와 헤더만 본다.
 *
 * <p>모두 {@code docs/API-목록.md} 의 "REST 로 보면" 에서 어긋난 것으로 찾은 자리다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class RestResponseIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** 인증번호 쿨다운 때문에 클래스 전체에서 한 번만 로그인한다. */
    private static CodeFlowLogin.Tokens admin;

    @LocalServerPort
    private int port;

    private RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
        if (admin == null) {
            admin = new CodeFlowLogin(port).adminWithOtp("admin@example.com");
        }
    }

    @Test
    @DisplayName("같은 코드로 역할을 두 번 만들면 두 번째는 409 다")
    void duplicateRoleIsConflict() {
        Map<String, String> body = Map.of("roleCode", "DUP_ROLE", "roleName", "중복 역할");

        assertThat(postAsAdmin("/api/admin/rbac/roles?realm=ADMIN", body).getStatusCode().value()).isEqualTo(201);
        ResponseEntity<String> again = postAsAdmin("/api/admin/rbac/roles?realm=ADMIN", body);

        assertThat(again.getStatusCode().value()).isEqualTo(409);
        assertThat(again.getBody()).contains("DUP_ROLE");
    }

    @Test
    @DisplayName("같은 코드로 권한을 두 번 만들면 두 번째는 409 다. 전에는 DB 유일 제약이 500 으로 나갔다")
    void duplicatePermissionIsConflict() {
        Map<String, String> body = Map.of("permissionCode", "DUP_PERM", "permissionName", "중복 권한",
                "category", "TEST");

        assertThat(postAsAdmin("/api/admin/rbac/permissions?realm=ADMIN", body).getStatusCode().value())
                .isEqualTo(201);
        ResponseEntity<String> again = postAsAdmin("/api/admin/rbac/permissions?realm=ADMIN", body);

        assertThat(again.getStatusCode().value()).isEqualTo(409);
        assertThat(again.getBody()).contains("DUP_PERM");
    }

    @Test
    @DisplayName("토큰 없이 관리 API 를 부르면 401 과 함께 Bearer 로 다시 오라고 알린다")
    void unauthorizedTellsHowToAuthenticate() {
        ResponseEntity<String> response = http.get().uri("/api/admin/rbac/roles?realm=ADMIN")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("토큰 엔드포인트의 응답은 성공이든 실패든 캐시에 남지 않는다")
    void tokenResponsesAreNotStored() {
        ResponseEntity<String> issued = token("grant_type=refresh_token&refresh_token=" + admin.refreshToken());
        ResponseEntity<String> rejected = token("grant_type=refresh_token&refresh_token=not-a-token");

        assertThat(issued.getStatusCode().value()).isEqualTo(200);
        assertThat(issued.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(issued.getHeaders().getFirst(HttpHeaders.PRAGMA)).isEqualTo("no-cache");
        assertThat(rejected.getStatusCode().value()).isEqualTo(400);
        assertThat(rejected.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    }

    private ResponseEntity<String> postAsAdmin(String path, Map<String, String> body) {
        return http.post().uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> token(String form) {
        return http.post().uri("/realms/admin/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().toEntity(String.class);
    }
}
