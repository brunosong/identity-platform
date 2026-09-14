package com.brunosong.identityplatform.auth.service.domain.identity;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * 이메일 계정 — 이메일(OTP)로 로그인하는 인증 수단. 한 {@link Principal} 에 매달린다(principalId 참조).
 *
 * <p>비밀번호가 없는(passwordless) 로그인 식별자다. OTP 코드 자체는 별도({@link EmailOtpChallenge})로 다루고,
 * 이 애그리거트는 "이 이메일이 어느 Principal 인가"의 매핑만 소유한다. auth 가 이메일로 주체를 독립 해석하도록
 * (customer/employee 등 다른 BC 비의존) auth 안에 둔다. 소셜(OAuth2)도 같은 방식의 별도 계정 애그리거트로 추가한다.
 *
 * <p>{@code realm} 을 함께 갖는다. 같은 사람이 직원이면서 포탈 고객일 수 있고 그 둘은 realm 이 다른
 * 별개 신원이라, 이메일의 유일성은 전역이 아니라 주체 realm 안에서만 성립한다. 조회할 때도 realm 을 함께 준다 —
 * 이메일만으로 찾으면 상대 realm 의 주체가 걸려 엉뚱한 토큰이 나간다.
 *
 * <h2>주소를 아는 것과 주소의 주인인 것은 다르다</h2>
 * {@code verified} 가 그 둘을 가른다. 같은 문자열이라도 <b>어떻게 들어왔는지</b>에 따라 뜻이 다르다.
 *
 * <ul>
 *   <li><b>확인됨</b> — 이메일 인증번호로 가입했거나, 그 주소로 OTP 로그인을 해냈거나,
 *       provider 가 검증한 소셜 이메일이다. 그 주소로 무엇이든 받아낸 사람이 있다는 뜻이다.</li>
 *   <li><b>확인 안 됨</b> — 비밀번호 가입 폼에 적혔거나 관리자가 대신 입력했다.
 *       <b>남의 주소일 수 있다.</b></li>
 * </ul>
 *
 * <p>이 구분이 없으면 소셜 자동 연결이 계정 탈취 경로가 된다 — 공격자가 남의 주소로 비밀번호
 * 가입을 해두면, 나중에 진짜 주인이 소셜로 들어올 때 그 신원에 붙어버린다(pre-account hijacking).
 * 그래서 자동 연결은 확인된 주소에만 허용한다.
 *
 * <p>생성 팩터리를 {@link #verified}/{@link #unverified} 둘로 나눈 것은 기본값을 두지 않기 위해서다.
 * 기본값이 있으면 새 호출부가 그것을 물려받고, 확인했는데도 확인 안 된 것으로 남거나 그 반대가 된다.
 * 여기서는 부르는 쪽이 <b>반드시</b> 답해야 한다.
 */
@Getter
public class EmailAccount {

    private final String emailAccountId;
    private final PrincipalId principalId;
    private final Realm realm;
    private final String email;
    private boolean verified;
    private final Instant createdAt;

    private EmailAccount(String emailAccountId, PrincipalId principalId, Realm realm,
                         String email, boolean verified, Instant createdAt) {
        this.emailAccountId = emailAccountId;
        this.principalId = principalId;
        this.realm = realm;
        this.email = email;
        this.verified = verified;
        this.createdAt = createdAt;
    }

    /** 소유가 확인된 주소 — 인증번호를 받아냈거나 provider 가 검증했다. */
    public static EmailAccount verified(PrincipalId principalId, Realm realm, String email) {
        return create(principalId, realm, email, true);
    }

    /** 적혀 있을 뿐 확인되지 않은 주소 — 남의 주소일 수 있다. */
    public static EmailAccount unverified(PrincipalId principalId, Realm realm, String email) {
        return create(principalId, realm, email, false);
    }

    private static EmailAccount create(PrincipalId principalId, Realm realm,
                                       String email, boolean verified) {
        if (principalId == null) throw new IllegalArgumentException("principalId must not be null");
        if (realm == null) throw new IllegalArgumentException("realm must not be null");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email must not be blank");
        return new EmailAccount(UUID.randomUUID().toString(), principalId, realm, email,
                verified, Instant.now());
    }

    public static EmailAccount restore(String emailAccountId, PrincipalId principalId, Realm realm,
                                       String email, boolean verified, Instant createdAt) {
        return new EmailAccount(emailAccountId, principalId, realm, email, verified, createdAt);
    }

    /**
     * 소유가 증명됐다. 되돌리는 연산은 없다 — 한 번 증명된 사실이 나중에 거짓이 되지 않는다.
     * 주소를 바꾸려면 그것은 <b>다른 계정</b>이고, 새 주소를 다시 증명해야 한다.
     */
    public void markVerified() {
        this.verified = true;
    }
}
