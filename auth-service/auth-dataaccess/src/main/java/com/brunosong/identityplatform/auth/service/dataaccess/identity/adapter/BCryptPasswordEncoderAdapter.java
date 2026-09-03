package com.brunosong.identityplatform.auth.service.dataaccess.identity.adapter;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PasswordEncoderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * {@link PasswordEncoderPort} 어댑터. 호스트가 제공하는 {@link PasswordEncoder}(BCrypt) 빈을 감싼다.
 */
@Component("authorizationBCryptPasswordEncoderAdapter")
@RequiredArgsConstructor
public class BCryptPasswordEncoderAdapter implements PasswordEncoderPort {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}
