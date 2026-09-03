package com.brunosong.identityplatform.auth.service.application.identity.ports.in.command;

import com.brunosong.identityplatform.auth.service.domain.identity.SocialProvider;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;

/**
 * 소셜 로그인 커맨드. subjectType 은 이 로그인이 어느 주체 도메인인지(portal=CUSTOMER),
 * authorizationCode 는 provider 콜백에서 받은 인가 코드다.
 */
public record SocialAuthCommand(SubjectType subjectType, SocialProvider provider, String authorizationCode) {
}
