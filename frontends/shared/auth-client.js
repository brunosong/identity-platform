/**
 * 브라우저용 auth 클라이언트.
 *
 * 백엔드의 auth-client 모듈과 같은 자리에 있다 — auth 의 내부를 모르고, 토큰과 클레임만 주고받는다.
 * 두 프론트엔드가 이 파일 하나를 공유한다. 실제 프로젝트라면 사내 npm 패키지가 될 자리다.
 *
 * 토큰은 메모리에만 둔다. localStorage 에 넣지 않는다 — 쿠키의 httpOnly 보호를 포기한 대가로,
 * 토큰이 오래 남는 자리에 두지 않는 것이 최소한의 완화다. 새로고침하면 로그아웃된다.
 */
export class AuthClient {

    /**
     * @param baseUrl auth-service 주소. 이 페이지의 출처와 다르므로 CORS 가 필요하다.
     * @param realm   이 앱이 상대하는 realm("customer" / "employee"). 앱마다 고정이다 —
     *                화면에서 고르게 두면 직원 화면으로 고객 로그인을 시도할 수 있게 된다.
     */
    constructor(baseUrl, realm) {
        this.baseUrl = baseUrl.replace(/\/+$/, '');
        this.realm = realm;
        this.tokens = null;
        this.listeners = [];
    }

    /** 요청이 오갈 때마다 부른다(화면 하단 로그용). */
    onRequest(listener) {
        this.listeners.push(listener);
    }

    get isLoggedIn() {
        return this.tokens !== null;
    }

    /** access 토큰에 실린 클레임. 서명은 확인하지 않는다 — 그건 서버가 할 일이다. */
    get claims() {
        return this.tokens ? decodeJwt(this.tokens.accessToken) : null;
    }

    // ── 인증 ────────────────────────────────────────────────────────────────

    /** 아이디/비밀번호 로그인. realm 은 경로가 정한다. */
    async loginWithPassword(loginId, password) {
        const res = await this.request('POST', `/api/auth/realms/${this.realm}/login`,
            { body: { loginId, password } });
        if (res.ok) this.tokens = res.data.tokens;
        return res;
    }

    /** 이메일 OTP — 인증번호 발송. 계정 열거 방지를 위해 가입 여부와 무관하게 202 다. */
    async sendEmailCode(email) {
        return this.request('POST', `/api/auth/realms/${this.realm}/login/email-otp/send-code`,
            { body: { email } });
    }

    /** 이메일 OTP — 검증하고 토큰을 받는다. */
    async loginWithEmailCode(email, verificationCode) {
        const res = await this.request('POST', `/api/auth/realms/${this.realm}/login/email-otp`,
            { body: { email, verificationCode } });
        if (res.ok) this.tokens = res.data.tokens;
        return res;
    }

    /**
     * 다른 realm 으로 같은 자격증명을 넣어본다 — realm 격리가 실제로 막는지 보는 실험용.
     * 성공해도 토큰을 보관하지 않는다.
     */
    async tryLoginInOtherRealm(otherRealm, loginId, password) {
        return this.request('POST', `/api/auth/realms/${otherRealm}/login`,
            { body: { loginId, password } });
    }

    /** 재발급. 권한과 리비전이 그 시점 값으로 다시 실린다. */
    async refresh() {
        if (!this.tokens) return { ok: false, status: 0, data: null };
        const res = await this.request('POST', `/api/auth/realms/${this.realm}/token/refresh`,
            { body: { refreshToken: this.tokens.refreshToken } });
        if (res.ok) this.tokens = res.data.tokens;
        return res;
    }

    /**
     * 로그아웃. 무효화 대상과 realm 은 모두 토큰에서 읽는다.
     * 단일 세션을 켜지 않았다면 이 호출 뒤에도 access 토큰은 만료까지 서버에서 유효하다 —
     * 그래서 여기서 직접 버린다. 토큰 폐기는 호출자 몫이다.
     */
    async logout() {
        const res = await this.request('POST', '/api/auth/logout', { auth: true });
        this.tokens = null;
        return res;
    }

    // ── 조회 ────────────────────────────────────────────────────────────────

    myPermissions() {
        return this.request('GET', '/api/auth/my-permissions', { auth: true });
    }

    /** 이 realm 의 공개키. JWKS 는 realm 마다 주소가 다르다. */
    jwks() {
        return this.request('GET', `/realms/${this.realm}/.well-known/jwks.json`);
    }

    // ── 밑바닥 ──────────────────────────────────────────────────────────────

    /**
     * 모든 호출이 지나는 자리.
     *
     * 쿠키를 쓰지 않으므로 브라우저가 알아서 붙여주는 자격증명이 없다. 토큰이 필요한 요청은
     * 보내는 쪽이 Authorization 헤더에 명시적으로 싣는다.
     */
    async request(method, path, { body = null, auth = false, query = null } = {}) {
        const headers = {};
        if (body) headers['Content-Type'] = 'application/json';
        if (auth && this.tokens) headers['Authorization'] = `Bearer ${this.tokens.accessToken}`;

        const url = this.baseUrl + path + (query ? '?' + new URLSearchParams(query) : '');

        let response;
        try {
            response = await fetch(url, {
                method, headers, body: body ? JSON.stringify(body) : undefined,
            });
        } catch (cause) {
            // fetch 자체가 실패하면 대개 서버가 없거나 CORS 에서 막힌 것이다.
            // 브라우저는 어느 쪽인지 스크립트에 알려주지 않는다(그것이 CORS 의 취지다).
            this.emit(method, path, 0);
            return {
                ok: false, status: 0, data: null,
                blocked: true,
                message: `요청이 브라우저에서 막혔습니다. auth-service 가 떠 있는지, `
                    + `app.cors.allowed-origins 에 ${location.origin} 이 들어 있는지 확인하세요.`,
            };
        }

        this.emit(method, path, response.status);

        const text = await response.text();
        const data = text ? safeJson(text) : null;
        return {
            ok: response.ok,
            status: response.status,
            data,
            message: data && data.message ? data.message : null,
        };
    }

    emit(method, path, status) {
        this.listeners.forEach(fn => fn({ method, path, status }));
    }
}

/** base64url 로 인코딩된 payload 를 편다. 한글이 섞여도 깨지지 않게 UTF-8 로 디코드한다. */
export function decodeJwt(token) {
    const part = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = part + '='.repeat((4 - part.length % 4) % 4);
    const bytes = Uint8Array.from(atob(padded), c => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes));
}

function safeJson(text) {
    try {
        return JSON.parse(text);
    } catch {
        return { message: text };
    }
}
