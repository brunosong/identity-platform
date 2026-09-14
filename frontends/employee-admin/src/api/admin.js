import { endpoints } from './config';
import { request } from './http';

/**
 * 관리 API — 계정을 만들고 인가 정책을 들여다본다.
 *
 * <h3>realm 이 두 번 나온다</h3>
 * <pre>
 * POST /api/admin/realms/admin/users
 *                            ^^^^^
 *                            계정을 만들 realm (대상)
 *      호출자의 realm 은 경로가 아니라 토큰이 정한다 — 언제나 어드민이다.
 * </pre>
 *
 * <b>인증하는 realm 과 조작하는 realm 은 다른 값이다.</b> 어드민이 포털 계정을 만드는 것은
 * 정상이므로 둘을 한 값으로 묶으면 안 된다. Keycloak 의 `/admin/realms/{realm}/users` 도 같은
 * 모양이다 — 호출자는 보통 master realm 토큰이고, 경로의 realm 은 관리 대상이다.
 *
 * <p>여기 있는 호출은 모두 `AUTHZ_MANAGE` 권한을 요구한다. 화면에서 메뉴를 감추는 것과는
 * 별개다 — <b>실제 방어는 서버가 한다.</b> 브라우저에서 이 코드를 고쳐 호출해도 403 이다.
 */

/** 계정을 만들 대상 realm. 지금은 어드민뿐이다 — 포털에는 셀프 가입이 있다. */
const TARGET_REALM = 'admin';

const authUrl = () => endpoints().auth;

/**
 * 직원 가입 — 관리자가 이메일로 계정을 만든다.
 *
 * <b>비밀번호를 받지 않는다.</b> 직원 계정은 이메일 계정만 갖고, 로그인은 그 주소로 오는
 * OTP 로 한다. 그래서 이 가입에는 "비밀번호를 정하세요" 단계가 없고, 만들어진 순간부터
 * 그 사람은 자기 메일함만 열면 들어올 수 있다.
 *
 * <p>`roleIds` 를 함께 보내면 초기 역할까지 배정된다. 비워 두면 계정은 생기지만 권한이 없어서,
 * 로그인은 되는데 아무 관리 화면도 열리지 않는다.
 */
export function registerEmployee(accessToken, { employeeId, name, email, mobile, roleIds }) {
    return request(authUrl(), 'POST', `/api/admin/realms/${TARGET_REALM}/users`, {
        token: accessToken,
        body: { employeeId, name, email, mobile, roleIds },
    });
}

export function roles(accessToken) {
    return request(authUrl(), 'GET', '/api/admin/rbac/roles', {
        token: accessToken, query: { realm: 'ADMIN' },
    });
}

export function permissions(accessToken) {
    return request(authUrl(), 'GET', '/api/admin/rbac/permissions', {
        token: accessToken, query: { realm: 'ADMIN' },
    });
}

/**
 * 인가 리비전. realm 전역 정책이 바뀔 때 올라가고, 발급된 토큰에 `rbacRev` 로 실려
 * 옛 토큰을 가려내는 데 쓴다.
 *
 * <p>개별 주체의 역할 부여/회수는 이 값을 올리지 않는다 — 그 변경은 그 사람의 다음 재발급
 * 때 반영된다.
 */
export function revision(accessToken) {
    return request(authUrl(), 'GET', '/api/admin/rbac/revision', {
        token: accessToken, query: { realm: 'ADMIN' },
    });
}
