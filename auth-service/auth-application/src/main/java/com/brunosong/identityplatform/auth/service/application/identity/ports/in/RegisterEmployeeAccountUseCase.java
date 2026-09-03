package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterEmployeeAccountCommand;

/**
 * 직원 계정 등록 인바운드 포트. auth 가 신원(esntlId + Principal)을 먼저 만들고, 같은 esntlId 로 employee
 * 프로필 생성을 명령한다. 반환값은 채번된 esntlId(역할 배정 등에 쓴다).
 */
public interface RegisterEmployeeAccountUseCase {

    String register(RegisterEmployeeAccountCommand command);
}
