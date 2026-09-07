package com.brunosong.identityplatform.auth.service.dataaccess.identity.adapter;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PasswordEncoderPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * {@link PasswordEncoderPort} 의 BCrypt 구현.
 *
 * <p>인코더를 이 어댑터가 직접 만든다. 전에는 호스트가 제공하는 {@link PasswordEncoder} 빈을 주입받아
 * 감쌌는데, 그러면 비밀번호 해시 방식을 auth 밖에서 정하게 된다 — 자격증명을 소유한 쪽이 정할 일이다.
 * (독립 서비스가 되면서 그 빈을 놓을 호스트도 없어졌다.)
 *
 * <p>다른 알고리즘으로 바꾸는 자리는 이 클래스가 아니라 포트다 — {@link PasswordEncoderPort} 의 다른
 * 구현으로 갈아끼운다. 해시 방식이 바뀌면 기존 해시를 읽는 방법(마이그레이션)도 함께 정해야 하므로,
 * 설정 한 줄로 갈아탈 수 있는 것처럼 보이지 않는 편이 정직하다.
 */
@Component("authorizationBCryptPasswordEncoderAdapter")
public class BCryptPasswordEncoderAdapter implements PasswordEncoderPort {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}
