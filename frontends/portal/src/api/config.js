/**
 * 이 앱이 상대하는 서버 주소.
 *
 * 기본값은 빌드 시점 환경변수(.env)에서 오고, 브라우저에서 덮어쓸 수 있다 — 로컬에서 포트를 바꿔
 * 띄우는 일이 잦아서다(8080 이 이미 쓰이고 있으면 8090 으로 옮기는 식). 덮어쓴 값은 이 브라우저에만
 * 남는다.
 *
 * 이 앱은 두 서버를 부른다. 게이트웨이가 없기 때문이다 — 게이트웨이가 있으면 주소는 하나가 되고,
 * 어느 서비스로 갈지는 경로가 정한다.
 */

const OVERRIDE_KEY = 'portal.endpoints';

const DEFAULTS = {
    auth: import.meta.env.VITE_AUTH_BASE_URL ?? 'http://localhost:8080',
    customer: import.meta.env.VITE_CUSTOMER_BASE_URL ?? 'http://localhost:8081',
};

function readOverride() {
    try {
        return JSON.parse(localStorage.getItem(OVERRIDE_KEY)) ?? {};
    } catch {
        // 손상된 값이 앱 전체를 막지 않게 한다.
        return {};
    }
}

export function endpoints() {
    const override = readOverride();
    return {
        auth: trimSlash(override.auth || DEFAULTS.auth),
        customer: trimSlash(override.customer || DEFAULTS.customer),
    };
}

export function saveEndpoints({ auth, customer }) {
    localStorage.setItem(OVERRIDE_KEY, JSON.stringify({ auth, customer }));
}

export function resetEndpoints() {
    localStorage.removeItem(OVERRIDE_KEY);
}

export function defaultEndpoints() {
    return { ...DEFAULTS };
}

/**
 * 구글 OAuth 클라이언트 ID.
 *
 * 공개값이다. 인가 요청 주소에 그대로 실려 나가므로 감출 수 있는 값이 아니다.
 * 감춰야 하는 것은 시크릿이고, 그건 auth-service 만 갖는다.
 */
export function googleClientId() {
    return import.meta.env.VITE_GOOGLE_CLIENT_ID ?? '';
}

function trimSlash(url) {
    return url.replace(/\/+$/, '');
}
