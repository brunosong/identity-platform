package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.event.PrincipalAuthenticatedEvent;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailOtpStore;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PasswordAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PasswordEncoderPort;
import com.brunosong.identityplatform.auth.service.application.identity.event.SubjectRegisteredEvent;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalAuthenticatedEventPublisher;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SubjectRegisteredEventPublisher;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalProfileRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SocialAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.TokenIssuerPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.RefreshChainRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.dto.RefreshedToken;
import com.brunosong.identityplatform.auth.service.application.identity.token.TokenProperties;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailOtpChallenge;
import com.brunosong.identityplatform.auth.service.domain.identity.PasswordAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.RefreshChain;
import com.brunosong.identityplatform.auth.service.domain.identity.PrincipalProfile;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialProvider;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.TokenPair;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * identity 서비스 테스트가 공유하는 인메모리 가짜 어댑터 모음. DB/암호화/토큰 발급 없이
 * 서비스의 오케스트레이션(잠금·멱등·주체 해석·이벤트 발행)만 검증하기 위한 것이다.
 */
final class IdentityFakes {

    private IdentityFakes() {
    }

    /** 호스트가 realm 별로 제공하는 선택 빈(ObjectProvider) 흉내. null 이면 미제공 호스트. */
    static <T> ObjectProvider<T> provider(T instance) {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                if (instance == null) {
                    throw new IllegalStateException("no bean");
                }
                return instance;
            }

            @Override
            public T getObject(Object... args) {
                return getObject();
            }

            @Override
            public T getIfAvailable() {
                return instance;
            }

            @Override
            public T getIfUnique() {
                return instance;
            }

            @Override
            public Iterator<T> iterator() {
                return instance == null ? List.<T>of().iterator() : List.of(instance).iterator();
            }
        };
    }

    /** 원문 앞에 접두어만 붙이는 가짜 인코더 — 해시 비교 동작만 재현한다. */
    static final class FakePasswordEncoder implements PasswordEncoderPort {
        @Override
        public String encode(String rawPassword) {
            return "hash:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            return encode(rawPassword).equals(passwordHash);
        }
    }

    static final class FakePrincipalRepository implements PrincipalRepository {
        final Map<String, Principal> byId = new LinkedHashMap<>();

        Principal seed(String subjectId, Realm realm) {
            return save(Principal.create(new SubjectId(subjectId), realm));
        }

        @Override
        public Optional<Principal> findById(PrincipalId principalId) {
            return Optional.ofNullable(byId.get(principalId.value()));
        }

        @Override
        public Optional<Principal> findBySubjectId(Realm realm, SubjectId subjectId) {
            return byId.values().stream()
                    .filter(p -> p.getRealm() == realm && p.getSubjectId().equals(subjectId))
                    .findFirst();
        }

        @Override
        public Principal save(Principal principal) {
            byId.put(principal.getPrincipalId().value(), principal);
            return principal;
        }
    }

    /** 표시 속성 저장. 가입이 실제로 이것을 채우는지 보려고 둔다. */
    static final class FakePrincipalProfileRepository implements PrincipalProfileRepository {
        final Map<String, PrincipalProfile> byPrincipalId = new LinkedHashMap<>();

        @Override
        public Optional<PrincipalProfile> findByPrincipalId(PrincipalId principalId) {
            return Optional.ofNullable(byPrincipalId.get(principalId.value()));
        }

        @Override
        public PrincipalProfile save(PrincipalProfile profile) {
            byPrincipalId.put(profile.getPrincipalId().value(), profile);
            return profile;
        }
    }

    static final class FakePasswordAccountRepository implements PasswordAccountRepository {
        /** 키가 유형+아이디다. 같은 loginId 가 realm 별로 따로 설 수 있다는 것이 이 저장소의 규칙이다. */
        final Map<String, PasswordAccount> byLoginId = new HashMap<>();
        int loginStateUpdates;

        private static String key(Realm realm, String loginId) {
            return realm + "|" + loginId;
        }

        @Override
        public Optional<PasswordAccount> findByLoginId(Realm realm, String loginId) {
            return Optional.ofNullable(byLoginId.get(key(realm, loginId)));
        }

        @Override
        public boolean existsByLoginId(Realm realm, String loginId) {
            return byLoginId.containsKey(key(realm, loginId));
        }

        @Override
        public PasswordAccount save(PasswordAccount account) {
            byLoginId.put(key(account.getRealm(), account.getLoginId()), account);
            return account;
        }

        @Override
        public void updateLoginState(PasswordAccount account) {
            loginStateUpdates++;
            byLoginId.put(key(account.getRealm(), account.getLoginId()), account);
        }
    }

    static final class FakeEmailAccountRepository implements EmailAccountRepository {
        /** 키가 유형+이메일이다. 같은 이메일이 realm 별로 따로 설 수 있다는 것이 이 저장소의 규칙이다. */
        final Map<String, EmailAccount> byEmail = new HashMap<>();

        private static String key(Realm realm, String email) {
            return realm + "|" + email;
        }

        @Override
        public Optional<EmailAccount> findByEmail(Realm realm, String email) {
            return Optional.ofNullable(byEmail.get(key(realm, email)));
        }

        @Override
        public Optional<EmailAccount> findByPrincipalId(PrincipalId principalId) {
            return byEmail.values().stream()
                    .filter(a -> a.getPrincipalId().equals(principalId))
                    .findFirst();
        }

        @Override
        public EmailAccount save(EmailAccount account) {
            byEmail.put(key(account.getRealm(), account.getEmail()), account);
            return account;
        }
    }

    static final class FakeEmailOtpStore implements EmailOtpStore {
        final List<EmailOtpChallenge> saved = new ArrayList<>();
        /** 별도 트랜잭션으로 남긴 실패 기록 수 — 인증 트랜잭션이 롤백돼도 남아야 하는 값이다. */
        int failedAttemptRecords;

        @Override
        public EmailOtpChallenge save(EmailOtpChallenge challenge) {
            saved.remove(challenge);
            saved.add(challenge);
            return challenge;
        }

        @Override
        public void recordFailedAttempt(EmailOtpChallenge challenge) {
            failedAttemptRecords++;
            save(challenge);
        }

        @Override
        public Optional<EmailOtpChallenge> findLatestUnused(String email) {
            return saved.stream()
                    .filter(c -> c.getEmail().equals(email) && !c.isUsed())
                    .reduce((first, second) -> second);
        }

        @Override
        public Optional<java.time.Instant> findLatestCreatedAt(String email) {
            return saved.stream()
                    .filter(c -> c.getEmail().equals(email))
                    .map(EmailOtpChallenge::getCreatedAt)
                    .reduce((first, second) -> second);
        }
    }

    static final class FakeSocialAccountRepository implements SocialAccountRepository {
        final List<SocialAccount> saved = new ArrayList<>();

        @Override
        public Optional<SocialAccount> findByProvider(Realm realm, SocialProvider provider,
                                                     String providerUid) {
            return saved.stream()
                    .filter(a -> a.getRealm() == realm && a.getProvider() == provider
                            && a.getProviderUid().equals(providerUid))
                    .findFirst();
        }

        @Override
        public SocialAccount save(SocialAccount account) {
            saved.add(account);
            return account;
        }
    }

    /**
     * 주체 등록 이벤트 발행 — 발행 사실만 기록한다.
     *
     * <p>재가입 판정은 이제 auth 의 이메일 계정으로 하므로 여기서 흉내낼 것이 없다. 발행 사실만 본다.
     */
    static final class RecordingSubjectRegisteredPublisher implements SubjectRegisteredEventPublisher {
        final List<SubjectRegisteredEvent> published = new ArrayList<>();

        @Override
        public void publish(SubjectRegisteredEvent event) {
            published.add(event);
        }
    }

    /**
     * 가짜 계보 저장소. 회전이 도는지 보려면 발급과 재발급이 같은 통을 봐야 한다.
     */
    static final class FakeRefreshChains implements RefreshChainRepository {
        final Map<String, RefreshChain> byFamilyId = new LinkedHashMap<>();

        @Override
        public void save(RefreshChain chain) {
            byFamilyId.put(chain.getFamilyId(), chain);
        }

        @Override
        public Optional<RefreshChain> findById(String familyId) {
            return Optional.ofNullable(byFamilyId.get(familyId));
        }

        @Override
        public void revoke(String familyId) {
            byFamilyId.remove(familyId);
        }
    }

    /** 계보 저장소와 토큰 수명 기본값을 묶어 준다. 대부분의 테스트는 둘 다 관심 밖이다. */
    static TokenIssuance tokenIssuance(TokenIssuerPort issuer) {
        return new TokenIssuance(issuer, new FakeRefreshChains(), new TokenProperties());
    }

    /** 토큰 형식은 관심 밖이라 subjectId 를 그대로 실어 준다. */
    static final class FakeTokenIssuer implements TokenIssuerPort {
        String refreshTokenSubjectId;
        /** 재발급이 낼 계보. 회전을 보는 테스트만 채운다. */
        RefreshedToken presented;
        /** 마지막으로 토큰에 실린 계보. 회전이 실제로 갈아끼웠는지 본다. */
        RefreshChain issuedChain;
        /** 마지막으로 발급을 요청받은 realm — 인증된 주체와 같은 realm 인지 보려고 남긴다. */
        Realm issuedRealm;
        Realm verifiedRealm;

        /** 마지막으로 발급을 요청받은 클라이언트 — aud 가 그 값에서 나온다. */
        /** 재발급이 refresh 토큰의 클라이언트를 그대로 쓰는지 보려고 둔다. */

        @Override
        public TokenPair issue(Realm realm, Principal principal, RefreshChain chain) {
            this.issuedRealm = realm;
            this.issuedChain = chain;
            return new TokenPair("access:" + realm + ":" + principal.getSubjectId().value(),
                    "refresh:" + principal.getSubjectId().value());
        }

        @Override
        public RefreshedToken readRefreshToken(Realm realm, String refreshToken) {
            this.verifiedRealm = realm;
            return presented != null ? presented
                    : new RefreshedToken(refreshTokenSubjectId, "family-1", "jti-1");
        }
    }

    static final class RecordingEventPublisher implements PrincipalAuthenticatedEventPublisher {
        final List<PrincipalAuthenticatedEvent> published = new ArrayList<>();

        @Override
        public void publish(PrincipalAuthenticatedEvent event) {
            published.add(event);
        }
    }
}
