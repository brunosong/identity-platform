/**
 * JWT 를 눈으로 보기 위한 도구. <b>검증하지 않는다</b> — 그건 서버가 할 일이다.
 *
 * payload 는 암호화가 아니라 base64url 인코딩일 뿐이라 누구나 읽을 수 있다. 이 화면이 그것을
 * 보여주는 것 자체가 학습 포인트다: 토큰에 담은 값은 공개된 것과 같다.
 */

export function decode(token) {
    if (!token) return null;
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    try {
        return {
            header: decodeSegment(parts[0]),
            payload: decodeSegment(parts[1]),
            signature: parts[2],
        };
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
    const s = Math.abs(seconds);
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = s % 60;
    return `${sign}${h > 0 ? `${h}시간 ` : ''}${m}분 ${sec}초`;
}

export function formatEpoch(seconds) {
    return seconds ? new Date(seconds * 1000).toLocaleString() : '-';
}

/**
 * 각 클레임이 무엇이고 왜 있는지. 화면에 같이 띄운다 —
 * 클레임 이름만 봐서는 왜 거기 있는지 알 수 없기 때문이다.
 */
export const CLAIM_NOTES = {
    iss: ['발급자', '누가 만든 토큰인가 — {auth 주소}/realms/{realm}. realm 이 이 안에 들어 있어서 별도의 realm 클레임이 없다. 소비 서비스는 이 값을 설정과 대조하므로, 서명이 맞아도 다른 배포(staging 등)가 만든 토큰이면 거부된다.'],
    sub: ['주체', '이 토큰이 누구에 대한 것인가. customer-service 가 이 값으로 프로필을 찾는다.'],
    aud: ['대상', '이 토큰을 받아들여도 되는 서비스들. 없으면 발급자만 맞으면 어디든 통해서, 서비스 하나가 침해되면 그 토큰을 같은 realm 의 다른 서비스에 그대로 재생할 수 있다. 한 클라이언트가 여럿 가질 수 있어서 통합 로그인은 깨지지 않는다 — 나열된 서비스 전부에 통한다.'],
    exp: ['만료', '이 시각이 지나면 거부된다. 짧을수록 탈취 피해가 작다.'],
    iat: ['발급 시각', ''],
    jti: ['토큰 ID', '개별 토큰을 폐기 목록에 올릴 때 쓴다.'],
    type: ['용도', 'access 인지 refresh 인지. refresh 를 access 처럼 쓰지 못하게 막는다.'],
    realm_access: ['영역 역할', '이 realm 에서 맡은 일. 조직도에 있는 이름에 가깝다 — 고객, 상담원 같은 것.'],
    resource_access: ['서비스별 역할', '서비스마다 갈린 역할. 그 이름이 무엇을 여는지는 그 서비스가 자기 코드로 정하고, auth 는 이름만 보관한다. 어휘가 서비스로 갈려 있어 order-service 의 READ 와 customer-service 의 READ 가 부딪히지 않는다. 자기 칸이 아닌 역할은 그 서비스의 문을 열지 않는다.'],
    rbacRev: ['정책 리비전', 'realm 전역 인가 정책이 바뀌면 올라간다. 옛 토큰을 가려낼 때 쓴다.'],
    nonce: ['논스', 'id_token 에만 있다. 로그인을 시작할 때 이 앱이 만든 값이 그대로 돌아온다. 다른 로그인의 id_token 을 끼워 넣으면 이 값이 맞지 않는다.'],
};
