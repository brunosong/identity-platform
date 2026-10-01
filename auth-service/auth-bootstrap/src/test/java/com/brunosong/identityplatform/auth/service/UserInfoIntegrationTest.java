package com.brunosong.identityplatform.auth.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterWithPasswordUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RequestRegistrationOtpUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithPasswordCommand;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * userinfo 가 토큰 주인의 정보를 주는지, 그리고 경로의 realm 키로만 검증하는지 본다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class UserInfoIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final String EMAIL = "hong@example.com";

    /** 클래스 전체에서 한 번만 가입하고 로그인한다. */
    private static CodeFlowLogin.Tokens customer;

    @LocalServerPort
    private int port;

    @Autowired
    private RequestRegistrationOtpUseCase requestRegistrationOtp;

    @Autowired
    private RegisterWithPasswordUseCase registerWithPassword;

    private final ObjectMapper json = new ObjectMapper();
    private RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
        if (customer == null) {
            requestRegistrationOtp.request(Realm.PORTAL, EMAIL);   // local 은 고정코드 123456
            registerWithPassword.register(new RegisterWithPasswordCommand(
                    Realm.PORTAL, EMAIL, "홍길동", "010-1234-5678", EMAIL, "pw12345678", "123456"));
            customer = new CodeFlowLogin(port).portalWithPassword(EMAIL, "pw12345678");
        }
    }

    @Test
    @DisplayName("토큰 주인의 sub, 이름, 이메일, 전화번호를 OIDC 이름으로 준다. 권한은 주지 않는다")
    void returnsTheOwner() throws Exception {
        ResponseEntity<String> response = call("GET", "/realms/portal/userinfo", customer.accessToken());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.get("sub").asText()).isNotBlank();
        assertThat(body.get("name").asText()).isEqualTo("홍길동");
        assertThat(body.get("email").asText()).isEqualTo(EMAIL);
        assertThat(body.get("phone_number").asText()).isEqualTo("010-1234-5678");
        assertThat(body.has("permissions")).isFalse();
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    @DisplayName("POST 로도 받는다. OIDC 가 둘 다 받으라고 한다")
    void acceptsPost() {
        assertThat(call("POST", "/realms/portal/userinfo", customer.accessToken()).getStatusCode().value())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("토큰이 없으면 401 이고 Bearer 로 다시 오라고 알린다")
    void withoutTokenIsUnauthorized() {
        ResponseEntity<String> response = call("GET", "/realms/portal/userinfo", null);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
    }

    @Test
    @DisplayName("포털 토큰을 직원 realm 자리에 내면 그 realm 의 키로 검증하다 떨어진다")
    void otherRealmIsRejected() {
        assertThat(call("GET", "/realms/admin/userinfo", customer.accessToken()).getStatusCode().value())
                .isEqualTo(401);
        assertThat(call("GET", "/realms/martian/userinfo", customer.accessToken()).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("refresh 토큰으로는 정보를 주지 않는다")
    void refreshTokenIsRejected() {
        assertThat(call("GET", "/realms/portal/userinfo", customer.refreshToken()).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("발급자 문서가 userinfo 주소를 알린다")
    void discoveryPointsToUserInfo() throws Exception {
        JsonNode document = json.readTree(http.get().uri("/realms/portal/.well-known/openid-configuration")
                .retrieve().body(String.class));

        assertThat(document.get("userinfo_endpoint").asText())
                .isEqualTo(document.get("issuer").asText() + "/userinfo");
    }

    private ResponseEntity<String> call(String method, String path, String token) {
        var request = "POST".equals(method) ? http.post().uri(path) : http.get().uri(path);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request.retrieve().toEntity(String.class);
    }
}
