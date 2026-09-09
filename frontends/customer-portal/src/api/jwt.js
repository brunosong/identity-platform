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
    iss: ['발급자', '누가 만든 토큰인가. 다른 시스템이 발급한 토큰을 걸러낸다.'],
    sub: ['주체', '이 토큰이 누구에 대한 것인가. customer-service 가 이 값으로 프로필을 찾는다.'],
    aud: ['대상', '어느 서비스가 받아들여도 되는가.'],
    exp: ['만료', '이 시각이 지나면 거부된다. 짧을수록 탈취 피해가 작다.'],
    iat: ['발급 시각', ''],
    jti: ['토큰 ID', '개별 토큰을 폐기 목록에 올릴 때 쓴다.'],
    type: ['용도', 'access 인지 refresh 인지. refresh 를 access 처럼 쓰지 못하게 막는다.'],
    realm: ['영역', '어드민(ADMIN)인가 포털(PORTAL)인가. JWKS 가 realm 마다 나뉘어 있어, 자기 realm 만 보는 서비스는 다른 realm 토큰을 서명 단계에서 거부한다.'],
    authLs: ['권한', '발급 시점의 권한 목록. 여기 있어서 서비스가 요청마다 권한 DB 를 뒤지지 않는다. 대신 발급 뒤의 권한 변경은 반영되지 않는다.'],
    rbacRev: ['정책 리비전', 'realm 전역 인가 정책이 바뀌면 올라간다. 옛 토큰을 가려낼 때 쓴다.'],
    sid: ['세션 ID', '단일 세션을 켠 경우에만. 다른 곳에서 로그인하면 이 값이 낡아 재발급이 거부된다.'],
};
