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

/** 고객 가입. 이메일이 곧 로그인 아이디다(고객은 별도 아이디가 없다). */
export function register({ email, password, name, phoneNumber }) {
    return request(authUrl(), 'POST', `/api/auth/realms/${REALM}/register`, {
        body: { email, password, name, phoneNumber },
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
