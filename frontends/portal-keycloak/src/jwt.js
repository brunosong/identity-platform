/**
 * 토큰을 눈으로 보기 위한 도구. 검증하지 않는다. 그건 토큰을 받는 서비스가 할 일이다.
 *
 * payload 는 암호화가 아니라 base64url 인코딩일 뿐이라 누구나 읽는다. 이 화면이 그것을
 * 그냥 풀어서 보여주는 것 자체가 학습 포인트다. 토큰에 담은 값은 공개된 것과 같다.
 */

export function decode(token) {
    if (!token) return null;
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    try {
        return { header: decodeSegment(parts[0]), payload: decodeSegment(parts[1]) };
    } catch {
        return null;
    }
}

function decodeSegment(segment) {
    const base64 = segment.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes));
}

/** 남은 시간(초). 음수면 이미 만료. */
export function secondsUntil(exp) {
    if (!exp) return null;
    return Math.floor(exp - Date.now() / 1000);
}

export function formatDuration(seconds) {
    if (seconds === null) return '-';
    const sign = seconds < 0 ? '-' : '';
    const total = Math.abs(seconds);
    const minutes = Math.floor(total / 60);
    return `${sign}${minutes}분 ${total % 60}초`;
}

/**
 * Keycloak 이 넣어주는 클레임이 무엇인지. 이름만 봐서는 왜 거기 있는지 알 수 없어서 같이 띄운다.
 */
export const CLAIM_NOTES = {
    iss: '발급자. Keycloak 주소 + realm 이다. realm 이 이 안에 들어 있어서 별도의 realm 클레임이 없다.',
    sub: '이 토큰이 누구에 대한 것인가. realm 안에서의 사용자 ID 다. 같은 사람이라도 realm 이 다르면 다른 값이 된다.',
    aud: '이 토큰을 받아들여도 되는 곳. 기본값으로는 Keycloak 내장 account 가 들어온다. 실제 서비스를 넣으려면 audience mapper 를 붙여야 한다.',
    azp: '토큰을 받아간 앱. 여기 있는 값이 client_id 다.',
    sid: 'SSO 세션 ID. 같은 realm 의 다른 앱이 로그인할 때 이 세션을 재사용해서 비밀번호를 다시 묻지 않는다.',
    typ: '용도. Bearer 인지 Refresh 인지. 리프레시 토큰을 액세스 토큰처럼 쓰지 못하게 막는다.',
    acr: '인증 강도. 1 이면 비밀번호 한 가지로 들어왔다는 뜻이다.',
    scope: '요청한 범위. openid 를 넣어야 id_token 이 함께 나온다.',
    realm_access: 'realm 전체에서 통하는 역할.',
    resource_access: 'client 안에서만 통하는 역할. 어휘가 client 로 갈려 있어 서로 부딪히지 않는다.',
    at_hash: 'id_token 에만 있다. 함께 받은 access_token 의 앞부분 해시라서, 둘이 한 쌍인지 확인할 수 있다.',
    nonce: '재생 공격 방어. 이 앱은 state 로 같은 일을 해서 쓰지 않는다.',
    exp: '이 시각이 지나면 거부된다.',
    iat: '발급 시각.',
    jti: '토큰 하나하나의 ID.',
};
