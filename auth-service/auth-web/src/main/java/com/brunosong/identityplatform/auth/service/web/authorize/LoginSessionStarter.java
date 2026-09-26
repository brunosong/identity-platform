package com.brunosong.identityplatform.auth.service.web.authorize;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.StartLoginSessionUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.domain.identity.LoginSession;
import com.brunosong.identityplatform.auth.service.web.support.LoginSessionCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class LoginSessionStarter {

    private final StartLoginSessionUseCase startLoginSession;

    public void start(AuthenticatedSubject subject, HttpServletRequest httpRequest, HttpServletResponse response) {

        // 이 브라우저가 로그인했다는 사실을 남긴다. 다음 앱은 이 쿠키로 화면을 건너뛴다.
        LoginSession session = startLoginSession.start(subject.realm(), subject.principalId());
        response.addHeader(HttpHeaders.SET_COOKIE,
                LoginSessionCookie.of(session, httpRequest.isSecure(), Instant.now()).toString());
    }
}
