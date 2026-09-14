import { endpoints } from './config';
import { request } from './http';

/**
 * auth-service 호출 — 어드민 realm.
 *
 * realm 은 경로가 정한다. 이 앱은 직원 앱이므로 항상 `admin` 이다 — 화면에서 고르게 두면
 * 직원 화면에서 고객으로 로그인하는 길이 생긴다.
 *
 * <h3>직원은 비밀번호가 없다</h3>
 * 로그인이 이메일 OTP 인 것은 화면의 취향이 아니라 <b>서버가 그렇게 생겼기 때문</b>이다 —
 * 직원 계정을 만드는 `RegisterEmployeeAccountService` 는 이메일 계정만 만들고 비밀번호 계정을
 * 만들지 않는다. 그래서 이 앱에는 비밀번호 입력칸이 아예 없다.
 *
 * <p>엔드포인트를 가르는 기준이 "누가"(직원/고객)가 아니라 "무엇으로"(비밀번호/OTP/소셜)
 * 로그인하느냐인 것도 같은 이야기다. `/login/email-otp` 는 realm 중립이고, 어느 realm 에서
 * 부르느냐에 따라 직원 OTP 가 되기도 고객 OTP 가 되기도 한다.
 */

const REALM = 'admin';

/**
 * 이 앱의 클라이언트 식별자. 로그인 요청에 실으면 auth 가 이 클라이언트의 audience 로 토큰을 만든다.
 *
 * 고객 포털과 다른 클라이언트라 audience 도 다르다 — 이 토큰은 customer-service 가 받지 않는다.
 * 통합 로그인이 깨지는 것이 아니라, 이 앱이 상대하는 서비스가 auth 하나뿐이라서다.
 */
const CLIENT_ID = 'employee-admin';

const authUrl = () => endpoints().auth;

/**
 * 인증번호 발송.
 *
 * <b>가입 여부와 무관하게 늘 202 다.</b> 없는 이메일에 404 를 주면 그 응답만으로 누가 직원인지
 * 훑어낼 수 있다(계정 열거). 그래서 응답만으로는 구분되지 않는 것이 의도다.
 *
 * local 프로파일은 메일을 보내지 않고 고정코드 123456 을 쓴다.
 */
export function sendCode(email) {
    return request(authUrl(), 'POST', `/api/auth/realms/${REALM}/login/email-otp/send-code`, {
        body: { email },
    });
}

/** 인증번호 검증 → 토큰 발급. 응답에 권한 목록도 함께 온다(화면 렌더용). */
export function login({ email, verificationCode }) {
    return request(authUrl(), 'POST', `/api/auth/realms/${REALM}/login/email-otp`, {
        body: { email, verificationCode, clientId: CLIENT_ID },
    });
}

/** 재발급. 권한과 리비전이 그 시점 값으로 다시 실린다 — 역할이 바뀐 뒤에는 이걸 받아야 보인다. */
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
 * JWKS 는 realm 마다 주소가 다르다. 어드민 키는 여기에만 있고 포털 주소에는 없다 —
 * 포털만 상대하는 서비스(customer-service)는 이 키를 아예 받지 못하고, 그래서 어드민 토큰은
 * 그 서비스의 코드가 한 줄도 돌기 전에 서명 검증에서 죽는다.
 */
export function jwks() {
    return request(authUrl(), 'GET', `/realms/${REALM}/.well-known/jwks.json`);
}

/**
 * 가입용 인증번호 발송.
 *
 * <b>로그인용과 조건이 정반대다.</b> 로그인용은 등록된 주소에만 보내고, 이것은 등록되지 <i>않은</i>
 * 주소에만 보낸다. 그래서 경로도 `/register/email/send-code` 로 따로 있다.
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
 * 인증번호로 가입. <b>비밀번호를 보내지 않는다.</b>
 *
 * 어드민 realm 은 이 방식만 열려 있다 — 직원은 비밀번호 계정을 갖지 않기 때문이다.
 * 토큰은 나오지 않는다(가입과 로그인은 별개).
 */
export function registerWithEmail({ email, name, phoneNumber, verificationCode }) {
    return request(authUrl(), 'POST', `/api/auth/realms/${REALM}/register/email`, {
        body: { email, name, phoneNumber, verificationCode },
    });
}

/**
 * 비밀번호 셀프 가입을 시도해 본다 — <b>404 여야 한다.</b>
 *
 * 포털이 쓰는 것과 같은 경로다. 어드민에 열리면 이 서비스가 만들 수 없는 계정(직원 + 비밀번호)을
 * 요구하는 경로가 생긴다. 그 정책은 컨트롤러가 아니라 Realm enum 이 들고 있다.
 */
export function tryPasswordRegister({ email, name }) {
    return request(authUrl(), 'POST', `/api/auth/realms/${REALM}/register`, {
        body: { email, name, password: 'pw12345678', phoneNumber: null },
    });
}

/** realm 격리 실험 — 같은 이메일로 포털 realm 에 인증번호를 요청해 본다. */
export function sendCodeToOtherRealm(realm, email) {
    return request(authUrl(), 'POST', `/api/auth/realms/${realm}/login/email-otp/send-code`, {
        body: { email },
    });
}

/** realm 격리 실험 — 그 코드로 포털 realm 로그인을 시도한다. 성공해도 토큰을 쓰지 않는다. */
export function loginToOtherRealm(realm, { email, verificationCode }) {
    // clientId 는 그대로 둔다. 이 클라이언트는 어드민 realm 소속이라 포털로는 토큰이 나오지 않는다 —
    // 자격증명이 맞아도 클라이언트 단계에서 먼저 걸린다.
    return request(authUrl(), 'POST', `/api/auth/realms/${realm}/login/email-otp`, {
        body: { email, verificationCode, clientId: CLIENT_ID },
    });
}

/** realm 격리 실험 — 어드민 realm 에서 소셜 로그인을 시도한다. 404 여야 한다. */
export function trySocialHere() {
    return request(authUrl(), 'POST', `/api/auth/realms/${REALM}/login/social`, {
        body: { provider: 'KAKAO', authorizationCode: 'x', clientId: CLIENT_ID },
    });
}
