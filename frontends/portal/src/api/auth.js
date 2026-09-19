import { endpoints } from './config';
import { request } from './http';

/**
 * auth-service 호출.
 *
 * realm 은 경로가 정한다. 이 앱은 포털(고객) 앱이므로 항상 `portal` 이다 — 화면에서 고르게 두면
 * 고객 화면에서 직원으로 로그인하는 길이 생긴다.
 *
 * realm 이름(PORTAL/ADMIN)과 주체 유형(CUSTOMER/EMPLOYEE)은 다른 값이다. 영역이 포털이고
 * 그 안에 사는 사람이 고객이다.
 *
 * 다만 격리를 눈으로 확인하는 실험(다른 realm 으로 시도)은 남겨둔다. 실험 결과로 받은 토큰은
 * 보관하지 않는다.
 */

const REALM = 'portal';

const authUrl = () => endpoints().auth;

/**
 * 구글에서 받은 code 를 넘겨 우리 토큰을 받는다.
 *
 * 여기서부터는 비밀번호 로그인과 완전히 같다. fetch 로 묻고 응답 본문으로 토큰을 받는다.
 * 구글을 다녀오는 리다이렉트 구간을 건너오기 위한 다리가 code 하나였을 뿐이다.
 *
 * 이 code 로 남의 계정에 들어갈 수는 없다. 교환은 auth-service 가 우리 client_id 와
 * client_secret 으로 하고, 다른 앱에게 발급된 code 는 구글이 거절한다.
 */
export function loginWithSocial(authorizationCode) {
    return request(authUrl(), 'POST', `/api/auth/realms/${REALM}/login/social`, {
        body: { provider: 'GOOGLE', authorizationCode },
    });
}

/** 고객 가입. 이메일이 곧 로그인 아이디다(고객은 별도 아이디가 없다). */
export function register({ email, password, name, phoneNumber }) {
    return request(authUrl(), 'POST', `/api/auth/realms/${REALM}/register`, {
        body: { email, password, name, phoneNumber },
    });
}

/**
 * 가입용 인증번호 발송.
 *
 * 로그인용 OTP 와 <b>조건이 정반대다.</b> 로그인용은 등록된 주소에만, 이것은 등록되지 <i>않은</i>
 * 주소에만 보낸다. 그래서 경로가 따로 있다.
 *
 * 응답은 어느 쪽이든 202 다 — "이미 가입된 이메일입니다" 를 돌려주면 주소를 넣어보는 것만으로
 * 누가 가입돼 있는지 훑을 수 있다.
 */
export function sendRegistrationCode(email) {
    return request(authUrl(), 'POST', `/api/auth/realms/${REALM}/register/email/send-code`, {
        body: { email },
    });
}

/**
 * 인증번호로 가입한다. <b>비밀번호를 정하지 않는다.</b>
 *
 * 비밀번호 가입과 달리 <b>가입 시점에 이메일 소유가 확인된다</b> — 인증번호를 받아낸 것이 곧
 * 그 주소의 주인이라는 증거이기 때문이다. 대신 이 사람은 이메일 OTP 로만 로그인한다.
 */
export function registerWithEmail({ email, name, phoneNumber, verificationCode }) {
    return request(authUrl(), 'POST', `/api/auth/realms/${REALM}/register/email`, {
        body: { email, name, phoneNumber, verificationCode },
    });
}

export function login({ loginId, password }) {
    return request(authUrl(), 'POST', `/api/auth/realms/${REALM}/login`, {
        body: { loginId, password },
    });
}

/** 재발급. 권한과 리비전이 그 시점 값으로 다시 실린다. */
export function refresh(refreshToken) {
    return request(authUrl(), 'POST', `/api/auth/realms/${REALM}/token/refresh`, {
        body: { refreshToken },
    });
}

/**
 * 로그아웃. 무효화 대상은 토큰에서, realm 은 경로에서 온다.
 *
 * 단일 세션을 켜지 않았다면 이 호출 뒤에도 access 토큰은 만료까지 서버에서 유효하다 —
 * 그래서 클라이언트가 직접 버려야 한다. 무상태 JWT 의 성질이다.
 */
export function logout(accessToken) {
    return request(authUrl(), 'POST', `/api/auth/realms/${REALM}/logout`, { token: accessToken });
}

export function myPermissions(accessToken) {
    return request(authUrl(), 'GET', `/api/auth/realms/${REALM}/my-permissions`, { token: accessToken });
}

/**
 * 이 realm 의 공개키. 브라우저가 볼 필요는 없지만 학습용으로 띄운다.
 *
 * JWKS 는 realm 마다 주소가 다르다. 그래서 포털만 상대하는 서비스는 어드민 키를 아예 받지 못하고,
 * 어드민 토큰은 서명 검증에서 죽는다 — 그 서비스의 코드가 한 줄도 돌기 전에.
 */
export function jwks() {
    return request(authUrl(), 'GET', `/realms/${REALM}/.well-known/jwks.json`);
}

/** realm 격리 실험 — 같은 자격증명을 다른 realm 에 넣어본다. 성공해도 토큰을 쓰지 않는다. */
export function tryOtherRealm(realm, { loginId, password }) {
    return request(authUrl(), 'POST', `/api/auth/realms/${realm}/login`, {
        body: { loginId, password },
    });
}
