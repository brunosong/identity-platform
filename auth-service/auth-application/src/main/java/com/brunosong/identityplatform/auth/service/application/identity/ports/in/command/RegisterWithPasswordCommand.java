package com.brunosong.identityplatform.auth.service.application.identity.ports.in.command;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 가입(주체 프로비저닝 + 자격증명) 명령. auth 가 realm 에 따라 대상 BC 에 주체를 생성하게 하고
 * Principal + 로컬 자격증명(아이디/비번)을 만든다.
 */
public record RegisterWithPasswordCommand(
        Realm realm,
        String email,
        String name,
        String phoneNumber,
        String loginId,
        String password
) {
}
