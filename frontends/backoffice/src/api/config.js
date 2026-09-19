/**
 * 이 앱이 상대하는 서버 주소.
 *
 * 기본값은 빌드 시점 환경변수(.env)에서 오고, 브라우저에서 덮어쓸 수 있다 — 로컬에서 포트를 바꿔
 * 띄우는 일이 잦아서다(8080 이 이미 쓰이고 있으면 8090 으로 옮기는 식). 덮어쓴 값은 이 브라우저에만
 * 남는다.
 *
 * <b>여기는 주소가 하나다.</b> 고객 포털은 auth 와 customer 둘을 부르지만, 직원 앱이 상대하는
 * 서비스는 아직 auth 뿐이다. 그래서 "토큰을 들고 다른 서비스로 간다" 는 흐름은 고객 포털에서만
 * 보인다.
 *
 * 저장 키에 앱 이름을 넣는다. 두 앱이 같은 브라우저에서 각자 다른 포트로 도는데, localStorage 는
 * 출처(origin)별로 갈리므로 사실 섞이지 않는다 — 그래도 이름을 갈라두면 devtools 에서 헷갈리지 않는다.
 */

const OVERRIDE_KEY = 'admin.endpoints';

const DEFAULTS = {
    auth: import.meta.env.VITE_AUTH_BASE_URL ?? 'http://localhost:8080',
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
    return { auth: trimSlash(override.auth || DEFAULTS.auth) };
}

export function saveEndpoints({ auth }) {
    localStorage.setItem(OVERRIDE_KEY, JSON.stringify({ auth }));
}

export function resetEndpoints() {
    localStorage.removeItem(OVERRIDE_KEY);
}

export function defaultEndpoints() {
    return { ...DEFAULTS };
}

function trimSlash(url) {
    return url.replace(/\/+$/, '');
}
