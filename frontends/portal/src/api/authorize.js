/**
 * 우리 인가 요청 - auth-service 로 떠난다. 로그인 화면을 거칠 수도, 안 거칠 수도 있다.
 *
 * 구글 버튼(google.js)과 구조가 같고 상대만 다르다. 다른 점이 하나 있다면 이쪽은 앱이 상대를
 * 고르지 않는다는 것이다. 구글이든 다른 무엇이든, 어떤 방법으로 사람을 확인할지는 auth 가 정한다.
 * 이 파일이 아는 것은 "우리 인증 서버로 보낸다" 뿐이다.
 *
 * 비밀번호를 받는 자리가 이 앱에서 사라진다는 것이 요점이다. 앱이 받는 것은 code 한 장이다.
 */

import { endpoints } from './config';
import { request } from './http';

/** 이 앱이 속한 realm. 고객 포털이므로 고정이다. */
const REALM = 'portal';

/** 등록할 때 auth 가 발급한 값(V9002 시드). 주소창에 그대로 실려 나가는 공개값이다. */
const CLIENT_ID = 'portal-17kqqi85h2ks';

/** 등록된 주소와 글자 그대로 같아야 한다. 슬래시 하나만 달라도 거절당한다. */
export const REDIRECT_URI = `${location.origin}/login/callback`;

/** 로그아웃하고 돌아올 자리. 이것도 등록된 주소여야 한다. */
const HOME = `${location.origin}/`;

const STATE_KEY = 'brunosong.state';
const VERIFIER_KEY = 'brunosong.verifier';

/** 로그인이 끝나면 어디로 보낼지. 브라우저가 앱을 떠나므로 메모리로는 안 된다. */
const RETURN_TO_KEY = 'brunosong.returnTo';

/**
 * 로그인 화면 주소.
 *
 * PKCE 검증값을 만들어 sessionStorage 에 남긴다. 브라우저가 이 페이지를 떠났다 돌아오므로
 * 메모리로는 안 된다. 나갈 때는 해시만 보내고, 원본은 code 를 토큰으로 바꾸러 갈 때 낸다.
 * 그래서 요청을 들여다본 쪽이 code 를 주워도 토큰으로 바꾸지 못한다.
 */
export async function authorizeUrl({ returnTo = null } = {}) {
    const state = randomString();
    const verifier = randomString();
    sessionStorage.setItem(STATE_KEY, state);
    sessionStorage.setItem(VERIFIER_KEY, verifier);
    if (returnTo) sessionStorage.setItem(RETURN_TO_KEY, returnTo);
    else sessionStorage.removeItem(RETURN_TO_KEY);

    const url = new URL(`${endpoints().auth}/realms/${REALM}/auth`);
    url.searchParams.set('response_type', 'code');
    url.searchParams.set('client_id', CLIENT_ID);
    url.searchParams.set('redirect_uri', REDIRECT_URI);
    // openid 가 있어야 신원을 담은 id_token 이 함께 나온다.
    url.searchParams.set('scope', 'openid');
    url.searchParams.set('state', state);
    url.searchParams.set('code_challenge', await sha256Base64Url(verifier));
    url.searchParams.set('code_challenge_method', 'S256');
    return url.toString();
}

/** 로그인이 끝났다. 어디로 보낼지 꺼내고 지운다. */
export function consumeReturnTo() {
    const path = sessionStorage.getItem(RETURN_TO_KEY);
    sessionStorage.removeItem(RETURN_TO_KEY);
    return path;
}

/**
 * 돌아온 state 가 이 브라우저가 시작한 것인지 확인한다. 한 번 쓰고 버린다.
 *
 * 막는 것은 로그인 CSRF 다. 공격자가 자기 계정으로 로그인을 시작해서 받은 콜백 주소를 피해자에게
 * 열게 하면, 피해자 브라우저가 공격자 계정으로 로그인된다. 피해자는 자기 계정인 줄 알고 쓰고
 * 나중에 공격자가 그 기록을 본다. 구글 쪽(google.js)에도 같은 검사가 있다.
 */
export function consumeState(received) {
    const started = sessionStorage.getItem(STATE_KEY);
    sessionStorage.removeItem(STATE_KEY);
    return Boolean(received) && received === started;
}

/**
 * 로그아웃 주소. auth 의 로그인 세션을 끊고 이 앱 홈으로 돌아온다.
 *
 * 앱이 들고 있는 토큰을 버리는 것과는 다른 일이다. 그쪽은 이 앱에서만 나가는 것이고,
 * 이것은 다른 앱에서도 로그인 화면이 다시 뜨게 만든다.
 */
export function logoutUrl() {
    const url = new URL(`${endpoints().auth}/realms/${REALM}/logout`);
    url.searchParams.set('client_id', CLIENT_ID);
    url.searchParams.set('post_logout_redirect_uri', HOME);
    return url.toString();
}

/**
 * 받은 code 를 토큰으로 바꾼다.
 *
 * 시크릿 대신 PKCE 원본을 낸다. 서버는 그것을 해시해서 로그인 시작할 때 받아둔 값과 맞춰본다.
 * 원본은 이 브라우저의 sessionStorage 에만 있었으므로, code 를 주운 쪽은 여기서 걸린다.
 *
 * 원본은 한 번 쓰고 버린다. code 도 서버에서 한 번 쓰면 사라지므로 재시도할 값이 아니다.
 */
export async function exchangeCode(code) {
    const verifier = sessionStorage.getItem(VERIFIER_KEY);
    sessionStorage.removeItem(VERIFIER_KEY);

    if (!verifier) {
        return { ok: false, status: 0, message: '이 브라우저가 시작한 로그인이 아닙니다.' };
    }

    const result = await request(endpoints().auth, 'POST', `/realms/${REALM}/token`, {
        form: {
            grant_type: 'authorization_code',
            code,
            redirect_uri: REDIRECT_URI,
            client_id: CLIENT_ID,
            code_verifier: verifier,
        },
    });

    if (!result.ok) {
        // 서버는 사유를 나누지 않는다. invalid_grant 하나로 온다.
        return { ...result, message: result.message ?? result.data?.error ?? '토큰 교환에 실패했습니다.' };
    }

    return { ok: true, status: result.status, tokens: toTokens(result.data) };
}

/**
 * refresh 토큰으로 새 쌍을 받는다.
 *
 * 코드 교환과 <b>같은 엔드포인트</b>다. 무엇을 하는 요청인지는 주소가 아니라 grant_type 이
 * 가른다(RFC 6749 6절). 토큰은 헤더가 아니라 폼 본문에 싣는다. 토큰 엔드포인트에서
 * Authorization 헤더는 클라이언트 인증이 쓰는 자리다.
 *
 * <b>돌아온 refresh 토큰을 반드시 갈아끼워야 한다.</b> 방금 낸 것은 이 호출로 죽는다(회전).
 * 옛 것을 계속 들고 있다가 다시 내면 서버가 탈취로 보고 그 계보를 통째로 끊는다.
 */
export async function refreshTokens(refreshToken) {
    if (!refreshToken) {
        return { ok: false, status: 0, message: '리프레시 토큰이 없습니다.' };
    }

    const result = await request(endpoints().auth, 'POST', `/realms/${REALM}/token`, {
        form: {
            grant_type: 'refresh_token',
            refresh_token: refreshToken,
            client_id: CLIENT_ID,
        },
    });

    if (!result.ok) {
        return { ...result, message: result.message ?? result.data?.error ?? '재발급에 실패했습니다.' };
    }

    return { ok: true, status: result.status, tokens: toTokens(result.data) };
}

/** 응답 이름이 snake_case 다. OAuth 명세의 모양이라 앱 쪽 이름으로 옮겨 담는다. */
function toTokens(data) {
    return {
        tokenType: data.token_type,
        accessToken: data.access_token,
        refreshToken: data.refresh_token,
    };
}

function randomString() {
    return base64Url(crypto.getRandomValues(new Uint8Array(32)));
}

async function sha256Base64Url(value) {
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
    return base64Url(new Uint8Array(digest));
}

/** 주소창에 실리는 값이라 +, /, = 를 쓰지 않는 방식으로 적는다(RFC 7636). */
function base64Url(bytes) {
    return btoa(String.fromCharCode(...bytes))
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=+$/, '');
}
