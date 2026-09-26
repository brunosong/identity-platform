package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePrincipalRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.RecordingEventPublisher;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가입 직후의 로그인 확정. 앱의 인가 요청으로 온 가입만 이것을 부른다.
 */
class RegisteredAuthenticationServiceTest {

    @Test
    @DisplayName("방금 가입한 사람의 인증 시각과 인증 이벤트를 남긴다")
    void recordsAuthentication() {
        FakePrincipalRepository principals = new FakePrincipalRepository();
        RecordingEventPublisher events = new RecordingEventPublisher();
        Principal principal = principals.seed("subject-1", Realm.PORTAL);
        RegisteredAuthenticationService service = new RegisteredAuthenticationService(
                principals, new AuthenticationCompletion(principals, events));

        AuthenticatedSubject subject = service.afterRegistration(
                new AuthenticatedSubject(principal.getPrincipalId(), "subject-1", Realm.PORTAL));

        assertThat(subject.subjectId()).isEqualTo("subject-1");
        assertThat(principal.getLastAuthenticatedAt()).isNotNull();
        assertThat(events.published).hasSize(1);
    }
}
