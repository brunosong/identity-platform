package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithPasswordCommand;

/**
 * 가입 인바운드 포트 — auth 가 흐름을 소유한다(주체 프로비저닝 → Principal/자격증명). (brunosong 참고)
 * 호스트는 SubjectProvisionPort 어댑터(대상 BC 위임)만 제공하는 껍데기.
 */
public interface RegisterWithPasswordUseCase {

    /**
     * 가입하고 가입한 사람을 돌려준다. 로그인은 시키지 않는다. 가입 뒤 로그인할지는 입구가 정한다
     * ({@link EstablishRegisteredAuthenticationUseCase}).
     *
     * <p>인증번호가 틀리면 AuthenticationFailedException, 이미 쓰이는 아이디나 이미 가입된 이메일이면
     * IllegalArgumentException.
     */
    AuthenticatedSubject register(RegisterWithPasswordCommand command);
}
