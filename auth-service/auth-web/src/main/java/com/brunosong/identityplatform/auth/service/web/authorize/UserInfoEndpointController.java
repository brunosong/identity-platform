package com.brunosong.identityplatform.auth.service.web.authorize;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.FindUserInfoUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.UserInfo;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * userinfo, {@code GET|POST /realms/{realm}/userinfo} (OIDC Core 5.3). 앱이 들고 있는 access 토큰의 주인이
 * 누구인지 사람이 읽는 정보로 돌려준다.
 *
 * <p>토큰 검증은 여기 오기 전에 스프링 시큐리티의 userinfo 체인이 끝냈다. 경로의 realm 키로 서명을 봤으니,
 * 여기 도착한 토큰의 {@code sub} 는 그 realm 의 주체다.
 *
 * <p>권한은 담지 않는다. 무엇을 할 수 있는지는 각 서비스가 판단한다.
 *
 * <p>응답을 캐시에 남기지 않는다. 개인 정보다.
 */
@RestController
@RequiredArgsConstructor
public class UserInfoEndpointController {

    private final AuthenticationRealm authenticationRealm;
    private final FindUserInfoUseCase findUserInfo;

    /** 토큰 주인의 sub, 이름, 이메일, 전화번호를 준다. OIDC 가 정한 대로 GET 과 POST 둘 다 받는다. */
    @RequestMapping(path = "/realms/{realm}/userinfo", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<UserInfoResponse> userinfo(@PathVariable String realm, @AuthenticationPrincipal Jwt token) {
        return findUserInfo.of(authenticationRealm.of(realm), token.getSubject())
                .map(info -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(UserInfoResponse.from(info)))
                // 서명은 맞는데 그 주체가 없다. 지워진 신원이다. 토큰을 더 쓸 수 없다는 뜻으로 401 이다.
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"")
                        .build());
    }

    /** userinfo 응답. 이름은 OIDC 표준 claim 그대로이고, 값이 없는 칸은 싣지 않는다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserInfoResponse(String sub, String name, String email,
                                   @JsonProperty("phone_number") String phoneNumber) {

        /** 유스케이스의 결과를 응답 모양으로 옮긴다. */
        static UserInfoResponse from(UserInfo info) {
            return new UserInfoResponse(info.subjectId(), info.name(), info.email(), info.phoneNumber());
        }
    }
}
