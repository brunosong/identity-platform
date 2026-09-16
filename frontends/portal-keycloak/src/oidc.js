/**
 * Authorization Code + PKCE 를 손으로 구현한 것.
 *
 * keycloak-js 어댑터를 쓰지 않는다. 어댑터는 이 파일에 있는 것을 전부 감춘다. 쓰기는 편하지만
 * 쓰고 나면 무엇이 오갔는지 알 수 없고, 이 앱은 그 오감을 보려고 만든 것이다.
 *
 * 흐름은 이렇게 간다.
 *
 *   1. 난수 code_verifier 를 만들고 그 SHA-256 을 code_challenge 로 보낸다
 *   2. 브라우저가 Keycloak 로그인 화면으로 떠난다. 비밀번호는 이 앱을 거치지 않는다
 *   3. 로그인에 성공하면 Keycloak 이 code 를 붙여 이 앱으로 돌려보낸다
 *   4. 그 code 와 원본 code_verifier 를 함께 보내 토큰으로 바꾼다
 *
 * code 는 주소창에 실려 오기 때문에 흘릴 구멍이 많다. 브라우저 히스토리, 리퍼러, 서버 로그,
 * 확장 프로그램. PKCE 가 막는 것이 그 지점이다. code 를 주워도 code_verifier 가 없으면
 * 토큰으로 바꿀 수 없다. challenge 는 해시라서 그것만 봐서는 verifier 를 되돌릴 수 없다.
 *
 * public client 라 시크릿이 없다. 시크릿이 하던 일(요청자가 진짜 그 앱인지 확인)을
 * verifier 가 요청 단위로 대신한다.
 */

const CONFIG = {
    baseUrl: (import.meta.env.VITE_KEYCLOAK_BASE_URL ?? 'http://localhost:8999').replace(/\/+$/, ''),
    realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'portal',
    clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'portal-keycloak',
};

/**
 * 돌아올 주소. 이 앱의 루트다.
 *
 * /callback 같은 전용 경로를 두지 않았다. 경로를 나누면 라우터가 필요한데, 돌아온 것은
 * 쿼리스트링에 code 가 있는지로 알 수 있어서 화면 하나로 충분하다.
 *
 * 이 값은 Keycloak 에 등록된 Valid redirect URIs 와 맞아야 하고, 토큰을 받을 때 한 번 더
 * 보낸다. 두 번 보내는 이유는 Keycloak 이 code 를 발급할 때의 주소와 같은지 대조하기 위해서다.
 */
export const REDIRECT_URI = `${location.origin}/`;

export const config = CONFIG;

const VERIFIER_KEY = 'pkce.verifier';
const STATE_KEY = 'oidc.state';
const LOG_KEY = 'oidc.log';

/**
 * 이 realm 의 엔드포인트 목록.
 *
 * 주소를 코드에 박지 않고 여기서 받아오는 것은 Spring Security 가 issuer-uri 한 줄로 하는 일과
 * 같다. 발급자만 알면 나머지 주소는 발급자가 알려준다.
 */
let discovered = null;

export async function discover() {
    if (discovered) return discovered;
    const url = `${CONFIG.baseUrl}/realms/${CONFIG.realm}/.well-known/openid-configuration`;
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(`디스커버리 실패 (${response.status}). ${url} 가 열려 있는지 확인하세요.`);
    }
    discovered = await response.json();
    log(`디스커버리: ${url}`);
    return discovered;
}

/** 로그인 시작. 이 함수가 끝나면 페이지가 Keycloak 으로 떠난다. */
export async function beginLogin() {
    const meta = await discover();

    const verifier = randomString(32);
    const challenge = await sha256Base64url(verifier);
    const state = randomString(16);

    // 페이지가 떠나므로 메모리에 둘 수 없다. 돌아왔을 때 같은 탭에서만 읽히면 되니
    // sessionStorage 를 쓴다. 토큰이 아니라 이번 로그인 한 번만 쓰는 난수다.
    sessionStorage.setItem(VERIFIER_KEY, verifier);
    sessionStorage.setItem(STATE_KEY, state);

    const params = new URLSearchParams({
        client_id: CONFIG.clientId,
        redirect_uri: REDIRECT_URI,
        response_type: 'code',
        // openid 가 있어야 id_token 이 함께 나온다. 없으면 access_token 만 온다.
        scope: 'openid profile email',
        state,
        code_challenge: challenge,
        code_challenge_method: 'S256',
    });

    log(`code_verifier 생성: ${verifier.slice(0, 12)}... (sessionStorage 에 보관)`);
    log(`code_challenge = SHA256(verifier): ${challenge.slice(0, 12)}...`);
    log(`인가 요청으로 이동: ${meta.authorization_endpoint}`);

    location.assign(`${meta.authorization_endpoint}?${params}`);
}

/** 주소창에 로그인 응답이 실려 있으면 꺼낸다. 없으면 null. */
export function readCallback() {
    const query = new URLSearchParams(location.search);
    if (query.get('error')) {
        return { error: query.get('error'), description: query.get('error_description') };
    }
    const code = query.get('code');
    return code ? { code, state: query.get('state') } : null;
}

/**
 * 주소창에서 code 를 지운다.
 *
 * 남겨두면 새로고침할 때마다 이미 써버린 code 로 다시 교환을 시도해서 실패한다. code 가
 * 히스토리와 북마크에 남는 것도 바람직하지 않다.
 */
export function clearCallbackFromUrl() {
    history.replaceState(null, '', location.pathname);
}

/** 받아온 code 를 토큰으로 바꾼다. */
export async function completeLogin({ code, state }) {
    const expectedState = sessionStorage.getItem(STATE_KEY);
    const verifier = sessionStorage.getItem(VERIFIER_KEY);
    sessionStorage.removeItem(STATE_KEY);
    sessionStorage.removeItem(VERIFIER_KEY);

    // state 는 CSRF 방어다. 공격자가 자기 code 를 이 앱에 떠넘겨 남의 계정으로
    // 로그인시키는 것을 막는다. 이 탭이 시작한 로그인이 아니면 여기서 끊긴다.
    if (!expectedState || state !== expectedState) {
        throw new Error('state 가 맞지 않습니다. 이 탭이 시작한 로그인이 아닙니다.');
    }
    if (!verifier) {
        throw new Error('code_verifier 가 없습니다. 로그인을 다시 시작하세요.');
    }

    log(`code 수신: ${code.slice(0, 12)}...`);
    log('code + code_verifier 를 토큰으로 교환');

    return tokenRequest({
        grant_type: 'authorization_code',
        code,
        redirect_uri: REDIRECT_URI,
        client_id: CONFIG.clientId,
        code_verifier: verifier,
    });
}

/** 재발급. 여기엔 PKCE 가 없다. 리프레시 토큰 자체가 자격증명이다. */
export async function refresh(refreshToken) {
    log('리프레시 토큰으로 재발급');
    return tokenRequest({
        grant_type: 'refresh_token',
        refresh_token: refreshToken,
        client_id: CONFIG.clientId,
    });
}

/**
 * 로그아웃 주소.
 *
 * 토큰을 버리는 것만으로는 부족하다. Keycloak 에 SSO 세션이 남아 있어서 다시 로그인하면
 * 비밀번호를 묻지 않고 그냥 통과한다. 세션까지 끊으려면 이쪽으로 브라우저를 보내야 한다.
 */
export async function logoutUrl(idToken) {
    const meta = await discover();
    const params = new URLSearchParams({
        id_token_hint: idToken,
        post_logout_redirect_uri: REDIRECT_URI,
    });
    return `${meta.end_session_endpoint}?${params}`;
}

/**
 * 토큰 엔드포인트는 JSON 을 받지 않는다. form-urlencoded 다.
 * JSON 으로 보내면 415 가 온다.
 */
async function tokenRequest(fields) {
    const meta = await discover();
    const response = await fetch(meta.token_endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(fields),
    });

    const data = await response.json().catch(() => null);
    if (!response.ok) {
        throw new Error(`${data?.error ?? response.status}: ${data?.error_description ?? '토큰 요청에 실패했습니다.'}`);
    }
    log(`토큰 수신 (access ${data.expires_in}초, refresh ${data.refresh_expires_in}초)`);
    return data;
}

// ── PKCE 원재료 ──────────────────────────────────────────
//
// crypto.subtle 은 보안 컨텍스트에서만 동작한다. localhost 는 https 가 아니어도
// 보안 컨텍스트로 쳐주므로 개발 중에는 그냥 된다. 사내 IP 로 띄우면 여기서 막힌다.

function randomString(byteLength) {
    return base64url(crypto.getRandomValues(new Uint8Array(byteLength)));
}

async function sha256Base64url(text) {
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
    return base64url(new Uint8Array(digest));
}

function base64url(bytes) {
    return btoa(String.fromCharCode(...bytes))
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=+$/, '');
}

// ── 흐름 기록 ────────────────────────────────────────────
//
// 페이지가 Keycloak 으로 떠났다 돌아오기 때문에 메모리에 쌓으면 그 사이가 지워진다.
// 떠나기 전에 무엇을 했는지가 이 앱에서 제일 볼 만한 부분이라 sessionStorage 에 남긴다.

function log(text) {
    const entries = readLog();
    entries.push({ at: new Date().toLocaleTimeString(), text });
    sessionStorage.setItem(LOG_KEY, JSON.stringify(entries));
}

export function readLog() {
    try {
        return JSON.parse(sessionStorage.getItem(LOG_KEY)) ?? [];
    } catch {
        return [];
    }
}

export function clearLog() {
    sessionStorage.removeItem(LOG_KEY);
}
