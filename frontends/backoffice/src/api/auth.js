import { endpoints } from './config';
import { request } from './http';

/**
 * auth-service 호출 — 어드민 realm.
 *
 * <h3>로그인은 여기 없다</h3>
 * 인가 코드 흐름으로 옮기면서 자격증명을 다루는 함수가 이 파일에서 사라졌다(api/authorize.js).
 * 남은 것은 이미 받은 토큰으로 부르는 것들이다 — 내 권한, 공개키. 가입도 auth 의 화면에서 한다.
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

const authUrl = () => endpoints().auth;



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
