/**
 * 인가 코드 흐름을 그대로 적은 파일. 라이브러리도 빌드도 없다.
 *
 * 포털(frontends/portal)이 React 로 하는 것과 같은 일이고, 여기서는 그 과정이 한 화면에 다 보인다.
 */

const AUTH = 'http://localhost:8080';
const REALM = 'portal';          // 포털과 같은 realm. 그래서 세션이 공유된다
const CLIENT_ID = 'shop-web';        // oauth_client 에 등록된 이름(V9004 시드)
export const REDIRECT_URI = 'http://localhost:5176/callback.html';
/** 로그아웃하고 돌아올 자리. 이것도 등록된 주소여야 한다. */
const HOME = 'http://localhost:5176/';

const STATE_KEY = 'shop.state';
const VERIFIER_KEY = 'shop.verifier';
const TOKEN_KEY = 'shop.tokens';

/** 1. 브라우저를 auth 로 보낼 주소. 해시만 보내고 원본은 여기 남긴다. */
export async function authorizeUrl() {
    const state = random();
    const verifier = random();
    sessionStorage.setItem(STATE_KEY, state);
    sessionStorage.setItem(VERIFIER_KEY, verifier);

    const url = new URL(`${AUTH}/realms/${REALM}/auth`);
    url.searchParams.set('response_type', 'code');
    url.searchParams.set('client_id', CLIENT_ID);
    url.searchParams.set('redirect_uri', REDIRECT_URI);
    url.searchParams.set('scope', 'openid');
    url.searchParams.set('state', state);
    url.searchParams.set('code_challenge', await sha256(verifier));
    url.searchParams.set('code_challenge_method', 'S256');
    return url.toString();
}

/** 로그아웃 주소. 세션을 끊고 이 앱 홈으로 돌려보내 달라고 한다. */
export function logoutUrl() {
    const url = new URL(`${AUTH}/realms/${REALM}/logout`);
    url.searchParams.set('client_id', CLIENT_ID);
    url.searchParams.set('post_logout_redirect_uri', HOME);
    return url.toString();
}

/** 2. 돌아온 state 가 이 브라우저가 시작한 것인지. 한 번 쓰고 버린다. */
export function consumeState(received) {
    const started = sessionStorage.getItem(STATE_KEY);
    sessionStorage.removeItem(STATE_KEY);
    return Boolean(received) && received === started;
}

/** 3. code 를 토큰으로 바꾼다. 시크릿 대신 PKCE 원본을 낸다. */
export async function exchange(code) {
    const verifier = sessionStorage.getItem(VERIFIER_KEY);
    sessionStorage.removeItem(VERIFIER_KEY);

    const response = await fetch(`${AUTH}/realms/${REALM}/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
            grant_type: 'authorization_code',
            code,
            redirect_uri: REDIRECT_URI,
            client_id: CLIENT_ID,
            code_verifier: verifier,
        }),
    });

    const body = await response.json();
    if (!response.ok) throw new Error(body.error ?? `교환 실패 (${response.status})`);

    sessionStorage.setItem(TOKEN_KEY, JSON.stringify(body));
    return body;
}

export function readTokens() {
    const raw = sessionStorage.getItem(TOKEN_KEY);
    return raw ? JSON.parse(raw) : null;
}

export function clearTokens() {
    sessionStorage.removeItem(TOKEN_KEY);
}

/** 토큰 가운데 토막이 클레임이다. 검증은 하지 않는다 - 그건 토큰을 받는 서비스의 일이다. */
export function claimsOf(accessToken) {
    const payload = accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(decodeURIComponent(escape(atob(payload))));
}

function random() {
    return base64Url(crypto.getRandomValues(new Uint8Array(32)));
}

async function sha256(value) {
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
    return base64Url(new Uint8Array(digest));
}

function base64Url(bytes) {
    return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
