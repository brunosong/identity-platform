package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithPasswordCommand;

/**
 * 가입 인바운드 포트 — auth 가 흐름을 소유한다(주체 프로비저닝 → Principal/자격증명). (brunosong 참고)
 * 호스트는 SubjectProvisionPort 어댑터(대상 BC 위임)만 제공하는 껍데기.
 */
public interface RegisterWithPasswordUseCase {

    /** 가입 후 principalId 반환. 중복 loginId 면 IllegalArgumentException. */
    String register(RegisterWithPasswordCommand command);
}
