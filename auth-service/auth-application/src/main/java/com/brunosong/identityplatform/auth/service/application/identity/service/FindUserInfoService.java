package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.FindSubjectByLoginUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.FindUserInfoUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.UserInfo;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PasswordAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalProfileRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.PasswordAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.PrincipalProfile;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
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
public class FindUserInfoService implements FindUserInfoUseCase, FindSubjectByLoginUseCase {

    private final PrincipalRepository principalRepository;
    private final PrincipalProfileRepository profileRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final PasswordAccountRepository passwordAccountRepository;

    /** 신원을 찾고, 그 신원의 프로필(이름, 전화번호)과 이메일 계정을 붙여 돌려준다. */
    @Override
    public Optional<UserInfo> of(Realm realm, String subjectId) {
        Optional<Principal> principal = principalRepository.findBySubjectId(realm, new SubjectId(subjectId));
        if (principal.isEmpty()) {
            return Optional.empty();
        }

        PrincipalId principalId = principal.get().getPrincipalId();
        Optional<PrincipalProfile> profile = profileRepository.findByPrincipalId(principalId);
        Optional<EmailAccount> emailAccount = emailAccountRepository.findByPrincipalId(principalId);

        return Optional.of(new UserInfo(
                subjectId,
                profile.map(PrincipalProfile::getName).orElse(null),
                emailAccount.map(EmailAccount::getEmail).orElse(null),
                profile.map(PrincipalProfile::getPhoneNumber).orElse(null)));
    }

    /** 로그인 계정에서 신원을 거쳐 subjectId 를 얻고, 나머지는 {@link #of} 와 같이 붙인다. */
    @Override
    public Optional<UserInfo> byLogin(Realm realm, String login) {
        Optional<PrincipalId> principalId = principalIdOf(realm, login.trim());
        if (principalId.isEmpty()) {
            return Optional.empty();
        }

        Optional<Principal> principal = principalRepository.findById(principalId.get());
        if (principal.isEmpty()) {
            return Optional.empty();
        }
        return of(realm, principal.get().getSubjectId().value());
    }

    private Optional<PrincipalId> principalIdOf(Realm realm, String login) {
        Optional<EmailAccount> emailAccount = emailAccountRepository.findByEmail(realm, login);
        if (emailAccount.isPresent()) {
            return Optional.of(emailAccount.get().getPrincipalId());
        }
        return passwordAccountRepository.findByLoginId(realm, login).map(PasswordAccount::getPrincipalId);
    }
}
