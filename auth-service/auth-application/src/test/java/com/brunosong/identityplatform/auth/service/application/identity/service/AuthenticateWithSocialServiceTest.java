package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.SocialAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SocialIdentityVerifierPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.dto.VerifiedSocialIdentity;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialProvider;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePrincipalProfileRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePrincipalRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakeSocialAccountRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakeEmailAccountRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.RecordingSubjectRegisteredPublisher;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakeTokenIssuer;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.RecordingEventPublisher;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.provider;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 소셜 로그인. 검증된 이메일 기준 자동 계정 연결과, 이메일을 못 받았을 때의 연결 거부가 핵심이다.
 */
class AuthenticateWithSocialServiceTest {

    private static final String CLIENT_ID = "test-client";

    private static final String PROVIDER_UID = "google-uid-1";
    private static final String EMAIL = "user@example.com";

    private FakeSocialAccountRepository socialRepo;
    private FakePrincipalRepository principalRepo;
    private FakePrincipalProfileRepository profileRepo;
    private FakeEmailAccountRepository emailAccountRepo;
    private RecordingEventPublisher eventPublisher;
    private RecordingSubjectRegisteredPublisher registeredPublisher;
    private StubVerifier verifier;

    @BeforeEach
    void setUp() {
        socialRepo = new FakeSocialAccountRepository();
        principalRepo = new FakePrincipalRepository();
        profileRepo = new FakePrincipalProfileRepository();
        emailAccountRepo = new FakeEmailAccountRepository();
        eventPublisher = new RecordingEventPublisher();
        registeredPublisher = new RecordingSubjectRegisteredPublisher();
        verifier = new StubVerifier(new VerifiedSocialIdentity(PROVIDER_UID, EMAIL, "홍길동"));
    }

    private AuthenticateWithSocialService service() {
        return new AuthenticateWithSocialService(
                provider(verifier), socialRepo, principalRepo, profileRepo, emailAccountRepo,
                registeredPublisher,
                new AuthenticationCompletion(principalRepo, eventPublisher,
                        new TokenIssuance(new FakeTokenIssuer())));
    }

    private SocialAuthCommand command() {
        return new SocialAuthCommand(Realm.PORTAL, SocialProvider.GOOGLE, "auth-code", CLIENT_ID);
    }

    @Test
    @DisplayName("처음 소셜로 들어오면 주체 등록을 알리고 소셜 계정을 연결한다")
    void firstLoginProvisionsAndLinks() {
        AuthenticationResult result = service().authenticate(command());

        assertThat(registeredPublisher.published).hasSize(1);
        assertThat(socialRepo.saved).hasSize(1);
        assertThat(result.realm()).isEqualTo(Realm.PORTAL);
        assertThat(eventPublisher.published).hasSize(1);
    }

    @Test
    @DisplayName("이미 연결된 소셜이면 계정을 다시 만들지 않고 그 Principal 로 발급한다")
    void secondLoginReusesLink() {
        AuthenticateWithSocialService service = service();
        AuthenticationResult first = service.authenticate(command());

        AuthenticationResult second = service.authenticate(command());

        assertThat(second.principalId()).isEqualTo(first.principalId());
        assertThat(socialRepo.saved).hasSize(1);
        assertThat(principalRepo.byId).hasSize(1);
    }

    @Test
    @DisplayName("같은 이메일로 이미 가입한 주체가 있으면 새 Principal 없이 소셜 수단만 붙는다")
    void linksToExistingSubjectByVerifiedEmail() {
        Principal existing = principalRepo.seed("customer-uuid-1", Realm.PORTAL);
        emailAccountRepo.save(EmailAccount.verified(
                existing.getPrincipalId(), Realm.PORTAL, EMAIL));

        AuthenticationResult result = service().authenticate(command());

        assertThat(result.subjectId()).isEqualTo("customer-uuid-1");
        assertThat(principalRepo.byId).hasSize(1);
        assertThat(registeredPublisher.published).isEmpty();
        assertThat(socialRepo.saved).hasSize(1);
    }

    @Test
    @DisplayName("provider 가 확인된 이메일을 주지 않으면 자동 연결하지 않는다(계정 탈취 방지)")
    void withoutVerifiedEmailRefusesToLink() {
        verifier = new StubVerifier(new VerifiedSocialIdentity(PROVIDER_UID, null, "홍길동"));

        assertThatThrownBy(() -> service().authenticate(command()))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("확인된 이메일");
        assertThat(socialRepo.saved).isEmpty();
    }

    @Test
    @DisplayName("소셜 검증 어댑터가 없으면 소셜 로그인을 지원하지 않는다")
    void withoutVerifierFails() {
        AuthenticateWithSocialService noVerifier = new AuthenticateWithSocialService(
                provider((SocialIdentityVerifierPort) null), socialRepo, principalRepo, profileRepo, emailAccountRepo,
                registeredPublisher,
                new AuthenticationCompletion(principalRepo, eventPublisher,
                        new TokenIssuance(new FakeTokenIssuer())));

        assertThatThrownBy(() -> noVerifier.authenticate(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SocialIdentityVerifierPort");
    }

    private record StubVerifier(VerifiedSocialIdentity identity) implements SocialIdentityVerifierPort {
        @Override
        public VerifiedSocialIdentity verify(SocialProvider provider, String authorizationCode) {
            return identity;
        }
    }

    @Test
    @DisplayName("기존 이메일이 확인되지 않은 것이면 자동 연결하지 않고 거절한다(pre-account hijacking 방지)")
    void unverifiedEmailIsNotAutoLinked() {
        // 공격자가 남의 주소로 비밀번호 가입을 해둔 상태를 흉내낸다 — 확인된 적이 없는 주소다.
        Principal planted = principalRepo.seed("attacker-uuid", Realm.PORTAL);
        emailAccountRepo.save(EmailAccount.unverified(
                planted.getPrincipalId(), Realm.PORTAL, EMAIL));

        assertThatThrownBy(() -> service().authenticate(command()))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("다른 방법으로 가입");

        // 공격자의 신원에 소셜 수단이 붙지 않았다.
        assertThat(socialRepo.saved).isEmpty();
        assertThat(principalRepo.byId).hasSize(1);
    }
}
