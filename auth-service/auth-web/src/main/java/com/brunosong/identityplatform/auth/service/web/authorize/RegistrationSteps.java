package com.brunosong.identityplatform.auth.service.web.authorize;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterWithEmailUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterWithPasswordUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RequestRegistrationOtpUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithEmailCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithPasswordCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.domain.shared.RegistrationMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 가입의 두 걸음. 가입 입구가 둘이라(앱의 인가 요청으로 온 가입, 가입만 하는 가입) 같은 규칙을 한 곳에 둔다.
 *
 * <p>입구가 다른 것은 앞(무엇을 들고 왔나)과 끝(로그인시키나)뿐이다. 가운데의 두 걸음은 같아야 한다.
 * 한쪽만 고쳐지면 그 입구로만 규칙이 느슨해진다.
 */
@Component
@RequiredArgsConstructor
class RegistrationSteps {

    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 64;

    private final RequestRegistrationOtpUseCase requestRegistrationOtp;
    private final RegisterWithPasswordUseCase registerWithPassword;
    private final RegisterWithEmailUseCase registerWithEmail;

    /**
     * 1단계. 가입용 인증번호를 보낸다.
     *
     * <p>보냈는지 아닌지를 돌려주지 않는다. 가입용 번호는 가입되지 않은 주소로만 나가는데, 이미
     * 가입된 주소라서 안 보냈다고 말하면 주소를 넣어보는 것만으로 누가 가입돼 있는지 훑을 수 있다.
     */
    void sendCode(Realm realm, String email, String name) {
        requireProfile(email, name);
        requestRegistrationOtp.request(realm, email.trim());
    }

    /**
     * 2단계. 인증번호를 확인하고 가입한다. 비밀번호를 적었으면 비밀번호 계정까지 만든다.
     *
     * <p>비밀번호를 비우면 인증번호로만 로그인하는 계정이 된다. 어느 쪽이든 이메일은 확인된 주소다.
     * 로그인은 시키지 않는다. 그것은 입구가 정한다.
     */
    AuthenticatedSubject register(Realm realm, String email, String name, String phoneNumber,
                                  String code, String password) {
        requireProfile(email, name);
        if (password == null || password.isEmpty()) {
            return registerWithEmail.register(new RegisterWithEmailCommand(
                    realm, email.trim(), name.trim(), blankToNull(phoneNumber), code));
        }
        // 길이를 먼저 본다. 인증번호는 확인하는 순간 쓰인 것이 되므로, 비밀번호가 틀려서 되돌려 보낼
        // 일이면 번호를 쓰기 전에 걸러야 사람이 번호를 다시 받지 않아도 된다.
        if (!realm.allowsSelfRegistrationWith(RegistrationMethod.PASSWORD)) {
            throw new IllegalArgumentException("이 영역은 비밀번호 가입을 지원하지 않습니다.");
        }
        if (password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new IllegalArgumentException(
                    "비밀번호는 " + PASSWORD_MIN + "자 이상 " + PASSWORD_MAX + "자 이하로 정하세요.");
        }
        return registerWithPassword.register(new RegisterWithPasswordCommand(
                realm, email.trim(), name.trim(), blankToNull(phoneNumber), email.trim(), password, code));
    }

    private static void requireProfile(String email, String name) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("이메일을 확인하세요.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름을 적어 주세요.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
