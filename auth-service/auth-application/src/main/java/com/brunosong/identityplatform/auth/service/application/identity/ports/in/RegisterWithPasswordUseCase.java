package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithPasswordCommand;

/**
 * 가입 인바운드 포트 — auth 가 흐름을 소유한다(주체 프로비저닝 → Principal/자격증명). (brunosong 참고)
 * 호스트는 SubjectProvisionPort 어댑터(대상 BC 위임)만 제공하는 껍데기.
 */
public interface RegisterWithPasswordUseCase {

    /**
     * 가입하고 그 사람으로 로그인까지 확정한다. 가입 화면에서 곧장 앱으로 돌아가기 때문이다.
     *
     * <p>이미 쓰이는 아이디나 이미 가입된 이메일이면 IllegalArgumentException.
     */
    AuthenticatedSubject register(RegisterWithPasswordCommand command);
}
