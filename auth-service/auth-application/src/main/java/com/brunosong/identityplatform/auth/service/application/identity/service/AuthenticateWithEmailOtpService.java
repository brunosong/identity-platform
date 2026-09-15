package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.AuthenticateWithEmailOtpUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.EmailOtpAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailOtpStore;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.TokenIssuerPort;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 OTP 로그인. auth 가 OTP 검증을 직접 소유한다 — 코드 검증은 auth 의 {@link EmailOtpStore},
 * 주체 해석은 {@link EmailAccountRepository#findByEmail}(이메일 → principalId → Principal)로 한다.
 * 토큰 형식만 호스트({@link TokenIssuerPort})가 정한다. 실패는 계정 열거 방지를 위해 동일 예외로 던진다.
 *
 * <p>코드 검증 자체는 {@link EmailOtpVerifier} 가 갖는다 — 가입도 같은 검증을 쓴다. 시도 횟수
 * 제한이 한쪽에만 있으면 그쪽이 무제한 대입 경로가 되기 때문에 한 자리에 둔다.
 *
 * <p><b>주체를 먼저 찾고 코드를 검증한다.</b> 순서가 뒤집히면 등록되지 않은 이메일에도 시도 횟수가
 * 쌓여, 남의 주소로 남의 챌린지를 잠글 수 있다.
 *
 * <p>성공하면 그 이메일 계정을 <b>확인됨</b>으로 올린다. 코드를 받아냈다는 것이 곧 그 주소의
 * 주인이라는 증거이기 때문이다. 비밀번호로 가입해 미확인으로 남아 있던 주소가 여기서 승격되고,
 * 그때부터 소셜 자동 연결의 대상이 된다.
 */
@Service
@RequiredArgsConstructor
public class AuthenticateWithEmailOtpService implements AuthenticateWithEmailOtpUseCase {

    private final EmailAccountRepository emailAccountRepository;
    private final PrincipalRepository principalRepository;
    private final EmailOtpVerifier otpVerifier;
    private final AuthenticationCompletion authenticationCompletion;

    @Override
    @Transactional
    public AuthenticationResult authenticate(EmailOtpAuthCommand command) {
        EmailAccount emailAccount = emailAccountRepository.findByEmail(command.realm(), command.email())
                .orElseThrow(() -> new AuthenticationFailedException("인증에 실패했습니다."));
        Principal principal = principalRepository.findById(emailAccount.getPrincipalId())
                .orElseThrow(() -> new AuthenticationFailedException("인증에 실패했습니다."));

        otpVerifier.verify(command.email(), command.otpCode());

        // 여기까지 왔다는 것은 그 주소로 보낸 코드를 받아냈다는 뜻이다 — 소유가 증명됐다.
        // 비밀번호로 가입해 미확인으로 남아 있던 주소가 이 시점에 확인됨으로 올라간다.
        if (!emailAccount.isVerified()) {
            emailAccount.markVerified();
            emailAccountRepository.save(emailAccount);
        }

        return authenticationCompletion.complete(principal);
    }
}
