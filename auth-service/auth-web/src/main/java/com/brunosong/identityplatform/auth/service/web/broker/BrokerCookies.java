package com.brunosong.identityplatform.auth.service.web.broker;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * 구글에 다녀오는 동안 들고 있는 쿠키 둘. 떠날 때 심고 돌아오면 꺼내면서 지운다.
 *
 * <ul>
 *   <li>{@code SOCIAL_STATE}: 떠날 때 만든 state. 돌아온 값과 같아야 이 브라우저가 시작한 흐름이다</li>
 *   <li>{@code SOCIAL_RESUME}: 다녀온 뒤 되돌아갈 인가 요청. <b>질의 문자열만</b> 담는다. 주소 전체를
 *       담으면, 누가 이 쿠키를 심어 둘 수 있을 때 아무 데로나 보내는 길이 된다. 경로는 컨트롤러가 붙이고,
 *       질의는 인가 요청의 입구가 다시 검증한다</li>
 * </ul>
 *
 * <p><b>SameSite 는 Lax 여야 한다.</b> 콜백은 구글(다른 사이트)이 브라우저를 밀어 보내는 최상위 이동이라,
 * Strict 로 두면 쿠키가 실리지 않아 늘 대조에 실패한다. Lax 는 이런 최상위 GET 이동에는 쿠키를 보낸다.
 *
 * <p>{@code Secure} 는 요청이 https 일 때만 켠다. http 로 띄운 로컬에서 켜면 브라우저가 저장하지 않는다.
 */
@Component
class BrokerCookies {

    private static final String STATE = "SOCIAL_STATE";
    private static final String RESUME = "SOCIAL_RESUME";
    private static final String PATH = "/realms";

    /** 구글에 다녀오는 시간이면 충분하다. 길게 두면 훔쳐 쓸 창만 넓어진다. */
    private static final Duration TTL = Duration.ofMinutes(5);

    /** 떠날 때 적어 두는 것. */
    record Trip(String state, String resumeQuery) {
    }

    void put(HttpServletRequest request, HttpServletResponse response, Trip trip) {
        add(request, response, STATE, trip.state(), TTL);
        // 질의 문자열에는 쿠키 값에 쓸 수 없는 글자가 섞일 수 있다. base64url 로 감싼다.
        add(request, response, RESUME, Base64.getUrlEncoder().withoutPadding()
                .encodeToString(trip.resumeQuery().getBytes(StandardCharsets.UTF_8)), TTL);
    }

    /**
     * 꺼내면서 지운다. 남겨두면 같은 값으로 두 번째 콜백을 받아줄 수 있게 된다. 둘 중 하나라도 없으면
     * 없는 것으로 친다.
     */
    Optional<Trip> take(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> state = read(request, STATE);
        Optional<String> resume = read(request, RESUME);
        add(request, response, STATE, "", Duration.ZERO);
        add(request, response, RESUME, "", Duration.ZERO);

        if (state.isEmpty() || resume.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Trip(state.get(),
                new String(Base64.getUrlDecoder().decode(resume.get()), StandardCharsets.UTF_8)));
    }

    private static void add(HttpServletRequest request, HttpServletResponse response, String name, String value,
                            Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(request.isSecure())
                .path(PATH)
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }
}
