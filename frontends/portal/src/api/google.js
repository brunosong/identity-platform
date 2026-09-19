/**
 * 구글 인가 요청.
 *
 * 이 파일이 생겼다는 것은 이 앱이 구글을 알게 됐다는 뜻이다. client_id 도, 구글 주소도,
 * 스코프도 여기 있다. 콜백을 앱이 직접 받기로 한 대가다.
 *
 * 인증 서버가 콜백을 받는 방식이었다면 이 파일은 없었다. 앱은 auth 로 보내기만 하고
 * 구글이라는 것이 있는지도 모른다. 대신 그쪽은 인증이 끝난 브라우저를 앱으로 돌려보내는
 * 길(우리 code 플로우)을 따로 만들어야 한다.
 */

import { googleClientId } from './config';

const AUTHORIZATION_ENDPOINT = 'https://accounts.google.com/o/oauth2/v2/auth';

/** 구글 콘솔의 Authorized redirect URIs 에 등록된 주소여야 한다. 글자 그대로 대조한다. */
export const REDIRECT_URI = `${location.origin}/callback`;

const STATE_KEY = 'google.state';

/**
 * 구글 로그인 화면 주소.
 *
 * state 를 만들어 sessionStorage 에 남긴다. 메모리로는 안 된다 - 브라우저가 이 페이지를 떠났다
 * 돌아오므로 자바스크립트 상태가 통째로 사라진다.
 */
export function authorizeUrl() {
    const state = randomString();
    sessionStorage.setItem(STATE_KEY, state);

    const url = new URL(AUTHORIZATION_ENDPOINT);
    url.searchParams.set('client_id', googleClientId());
    url.searchParams.set('redirect_uri', REDIRECT_URI);
    // 토큰이 아니라 code 를 달라는 것. 토큰을 바로 주는 방식(implicit)은 토큰이 주소창에
    // 실려 오기 때문에 쓰지 않는다.
    url.searchParams.set('response_type', 'code');
    // openid 가 있어야 응답에 id_token 이 실린다. 그게 없으면 사람 정보를 알아내려고
    // 서버가 userinfo 를 따로 불러야 한다.
    url.searchParams.set('scope', 'openid email profile');
    url.searchParams.set('state', state);
    return url.toString();
}

/**
 * 돌아온 state 가 이 브라우저가 시작한 것인지 확인한다. 한 번 쓰고 버린다.
 *
 * 막는 것은 로그인 CSRF 다. 공격자가 자기 구글 계정으로 시작해 받은 콜백 주소를 피해자에게
 * 열게 하면, 피해자 브라우저에 공격자 계정으로 로그인된 상태가 만들어진다. 피해자는 자기
 * 계정인 줄 알고 쓰고, 나중에 공격자가 그것을 본다.
 */
export function consumeState(received) {
    const started = sessionStorage.getItem(STATE_KEY);
    sessionStorage.removeItem(STATE_KEY);
    return Boolean(received) && received === started;
}

function randomString() {
    const bytes = crypto.getRandomValues(new Uint8Array(32));
    return btoa(String.fromCharCode(...bytes)).replace(/[+/=]/g, '');
}
