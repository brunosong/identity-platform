package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.FindUserInfoUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.UserInfo;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalProfileRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.PrincipalProfile;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 신원, 프로필, 이메일 계정을 묶어 읽는다. 셋이 각자 다른 표에 있다. 신원은 누구인가만, 프로필은
 * 사람이 읽는 이름과 연락처를, 이메일 계정은 로그인 식별자를 갖는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FindUserInfoService implements FindUserInfoUseCase {

    private final PrincipalRepository principalRepository;
    private final PrincipalProfileRepository profileRepository;
    private final EmailAccountRepository emailAccountRepository;

    @Override
    public Optional<UserInfo> of(Realm realm, String subjectId) {
        return principalRepository.findBySubjectId(realm, new SubjectId(subjectId)).map(principal -> {
            Optional<PrincipalProfile> profile = profileRepository.findByPrincipalId(principal.getPrincipalId());
            return new UserInfo(
                    subjectId,
                    profile.map(PrincipalProfile::getName).orElse(null),
                    emailAccountRepository.findByPrincipalId(principal.getPrincipalId())
                            .map(EmailAccount::getEmail).orElse(null),
                    profile.map(PrincipalProfile::getPhoneNumber).orElse(null));
        });
    }
}
