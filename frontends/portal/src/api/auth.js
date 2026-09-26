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
 * 로그인과 가입은 여기 없다. 둘 다 auth 의 화면에서 하고, 토큰은 code 교환으로만 받는다
 * (authorize.js).
 */

const REALM = 'portal';

const authUrl = () => endpoints().auth;

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
