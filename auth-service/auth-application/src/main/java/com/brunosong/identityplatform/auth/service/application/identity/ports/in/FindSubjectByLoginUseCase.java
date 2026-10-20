package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.UserInfo;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.Optional;

/**
 * 운영자가 아는 값으로 사람을 찾는다. 운영자는 subjectId 를 모르고 이메일이나 로그인 아이디를 안다.
 *
 * <p>이메일 계정을 먼저 보고, 없으면 비밀번호 계정의 로그인 아이디로 본다. 소셜 계정만 있는 사람은
 * 여기서 찾을 수 없다.
 */
public interface FindSubjectByLoginUseCase {

    /** 그 realm 에서 이메일이나 로그인 아이디가 login 인 사람. 없으면 비어 있다. */
    Optional<UserInfo> byLogin(Realm realm, String login);
}
