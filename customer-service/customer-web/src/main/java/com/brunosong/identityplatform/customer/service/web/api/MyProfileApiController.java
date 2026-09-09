package com.brunosong.identityplatform.customer.service.web.api;

import com.brunosong.identityplatform.customer.service.application.ports.in.CustomerProfileUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 고객 본인의 프로필 API.
 *
 * <p><b>조회 키를 요청에서 받지 않는다.</b> 토큰의 {@code sub} 로만 찾는다 — 경로나 본문으로 받으면
 * 남의 식별자를 적어 넣는 것으로 남의 프로필을 열 수 있다. 이것이 게이트웨이가 대신해 줄 수 없는
 * 확인이다. 게이트웨이는 "이 URL 에 들어와도 되는가"까지 알지만 "이 데이터가 이 사람 것인가"는 모른다.
 *
 * <p>인증 코드가 한 줄도 없다. 여기 도달했다는 것이 곧 검증을 통과했다는 뜻이다 —
 * {@code SecurityConfiguration} 의 필터가 모든 요청 앞에 있고 기본값이 거부이기 때문이다.
 * 전에는 컨트롤러마다 {@code caller.require(request)} 를 불러야 했고, 빠뜨리면 그 API 가 열렸다.
 *
 * <p>realm 확인도 없다. 이 서비스가 포털 발급자만 상대한다는 사실이 설정에 있고
 * ({@code spring.security.oauth2.resourceserver.jwt.issuer-uri}), 어드민 토큰은 그 발급자의 JWKS 에
 * 없는 키로 서명돼 있어 필터에서 죽는다.
 */
@RestController
@RequestMapping("/api/customers/me")
@RequiredArgsConstructor
public class MyProfileApiController {

    private final CustomerProfileUseCase customerProfiles;

    /** 아직 프로필을 만들지 않았으면 404 다. 빈 프로필을 지어내 돌려주지 않는다. */
    @GetMapping
    public ResponseEntity<ProfileResponse> myProfile(@AuthenticationPrincipal Jwt token) {
        return customerProfiles.find(token.getSubject())
                .map(profile -> ResponseEntity.ok(ProfileResponse.of(profile)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 없으면 만들고 있으면 고친다. 프로필은 주체당 하나뿐이라 생성과 수정을 가르지 않는다.
     *
     * <p>이메일을 여기서 고쳐도 <b>로그인 이메일은 바뀌지 않는다.</b> 그것은 auth 가 소유한다.
     * 두 곳의 이메일이 갈라질 수 있다는 뜻이고, 그것이 서비스를 나눈 대가다.
     */
    @PutMapping
    public ProfileResponse save(@AuthenticationPrincipal Jwt token,
                                @Valid @RequestBody SaveProfileRequest body) {
        return ProfileResponse.of(customerProfiles.save(
                token.getSubject(), body.name(), body.phoneNumber(), body.email()));
    }

    public record SaveProfileRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 30) String phoneNumber,
            @Email @Size(max = 255) String email) {
    }
}
