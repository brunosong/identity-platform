/**
 * 우리 인가 요청 - auth-service 의 로그인 화면으로 떠난다.
 *
 * 구글 버튼(google.js)과 구조가 같고 상대만 다르다. 다른 점이 하나 있다면 이쪽은 앱이 상대를
 * 고르지 않는다는 것이다. 구글이든 다른 무엇이든, 어떤 방법으로 사람을 확인할지는 auth 가 정한다.
 * 이 파일이 아는 것은 "우리 인증 서버로 보낸다" 뿐이다.
 *
 * 비밀번호를 받는 자리가 이 앱에서 사라진다는 것이 요점이다. 앱이 받는 것은 code 한 장이다.
 */

import { endpoints } from './config';

/** 이 앱이 속한 realm. 고객 포털이므로 고정이다. */
const REALM = 'portal';

/** oauth_client 에 등록한 이름. 주소창에 그대로 실려 나가는 공개값이다. */
const CLIENT_ID = 'portal';

/** 등록된 주소와 글자 그대로 같아야 한다. 슬래시 하나만 달라도 거절당한다. */
export const REDIRECT_URI = `${location.origin}/login/callback`;

const STATE_KEY = 'brunosong.state';
const VERIFIER_KEY = 'brunosong.verifier';

/**
 * 로그인 화면 주소.
 *
 * PKCE 검증값을 만들어 sessionStorage 에 남긴다. 브라우저가 이 페이지를 떠났다 돌아오므로
 * 메모리로는 안 된다. 나갈 때는 해시만 보내고, 원본은 code 를 토큰으로 바꾸러 갈 때 낸다.
 * 그래서 요청을 들여다본 쪽이 code 를 주워도 토큰으로 바꾸지 못한다.
 */
export async function authorizeUrl() {
    const state = randomString();
    const verifier = randomString();
    sessionStorage.setItem(STATE_KEY, state);
    sessionStorage.setItem(VERIFIER_KEY, verifier);

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
