package com.brunosong.identityplatform.auth.service.web.admin.console.login;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 쿠키의 토큰으로 이 요청이 누구인지 정한다. 검증과 refresh 갱신은 {@link ConsoleLogin} 이 한다.
 *
 * <p>통과하면 스프링 시큐리티에 "MASTER 의 이 사람" 으로 등록하고, 못 하면 아무것도 하지 않는다.
 * 막는 것은 이 필터가 아니라 체인의 {@code authenticated()} 다. 그래서 이 필터가 무엇을 빠뜨려도
 * 열리지 않고 막히는 쪽으로 끝난다.
 *
 * <p>빈으로 등록하지 않는다. 필터를 빈으로 만들면 스프링 부트가 모든 요청 앞에 따로 걸어 버린다.
 * 운영 화면 체인 안에서만 돌아야 한다.
 *
 * <p>로그인이 돌아오는 콜백과 로그아웃에서는 돌지 않는다. 거기서 refresh 를 갱신하면 곧 버릴 토큰을
 * 만들고, 로그아웃은 낸 refresh 의 계보를 끊으니 갱신이 오히려 일을 늘린다.
 */
@RequiredArgsConstructor
class ConsoleAuthenticationFilter extends OncePerRequestFilter {

    private final ConsoleLogin consoleLogin;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> subject = consoleLogin.authenticate(request, response);
        if (subject.isPresent()) {
            Authentication loggedIn = UsernamePasswordAuthenticationToken.authenticated(subject.get(), null, List.of());
            SecurityContextHolder.getContext().setAuthentication(loggedIn);
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals(ConsoleLogin.CALLBACK_PATH) || path.equals(ConsoleLogin.LOGOUT_PATH);
    }
}
