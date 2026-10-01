import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { exchangeCode, refreshTokens } from '../api/authorize';
import { decode } from '../api/jwt';

/**
 * 로그인 상태를 들고 있는 곳.
 *
 * <h3>토큰을 어디에 두나</h3>
 * 기본은 <b>메모리</b>다. 새로고침하면 사라진다 — 불편하지만 그게 정직한 기본값이다.
 *
 * 쿠키의 {@code httpOnly} 를 포기하고 본문으로 토큰을 받기로 한 순간, 토큰은 스크립트가 읽을 수
 * 있는 자리에 놓인다. XSS 하나면 털린다는 뜻이다. 그때 남은 완화책은 "오래 남는 자리에 두지 않기"
 * 뿐이다. localStorage 는 탭을 닫아도, 브라우저를 껐다 켜도 남는다.
 *
 * <h3>auth 를 관리하는 앱이 아니다</h3>
 * 전에는 여기서 직원을 등록하고 인가 정책을 봤다. 그 일은 MASTER realm 의 관리자가 auth 운영 화면에서
 * 한다. 그래서 이 앱은 자기 권한을 auth 에 묻지 않는다. 업무 화면이 생기면 그 업무 서비스가 권한을 본다.
 */

const AuthContext = createContext(null);

const PERSIST_FLAG = 'admin.persistTokens';
const TOKEN_KEY = 'admin.tokens';

export function AuthProvider({ children }) {
    const [persist, setPersistState] = useState(() => localStorage.getItem(PERSIST_FLAG) === 'true');
    const [tokens, setTokensState] = useState(() => (
        localStorage.getItem(PERSIST_FLAG) === 'true' ? readStoredTokens() : null
    ));

    const setTokens = useCallback((next) => {
        setTokensState(next);
        if (localStorage.getItem(PERSIST_FLAG) === 'true') {
            if (next) localStorage.setItem(TOKEN_KEY, JSON.stringify(next));
            else localStorage.removeItem(TOKEN_KEY);
        }
    }, []);

    /** 보관 방식을 바꾼다. 끄면 이미 저장된 토큰도 지운다 — 켜둔 동안 남은 흔적을 남기지 않는다. */
    const setPersist = useCallback((enabled) => {
        localStorage.setItem(PERSIST_FLAG, String(enabled));
        setPersistState(enabled);
        if (!enabled) localStorage.removeItem(TOKEN_KEY);
    }, []);

    useEffect(() => {
        if (persist && tokens) localStorage.setItem(TOKEN_KEY, JSON.stringify(tokens));
    }, [persist, tokens]);

    /**
     * 인증 서버에서 받은 code 로 로그인한다.
     *
     * 이 앱이 자격증명을 다루는 자리는 이제 없다. 사람을 어떻게 확인했는지(인증번호였는지
     * 다른 무엇이었는지)도 모르고, 손에 쥐는 것은 교환해서 받은 토큰뿐이다.
     */
    const loginWithCode = useCallback(async (code) => {
        const result = await exchangeCode(code);
        if (result.ok) setTokens(result.tokens);
        return result;
    }, [setTokens]);

    /**
     * 재발급. 받은 쌍을 <b>통째로</b> 갈아끼운다. access 만 챙기고 refresh 를 두면, 이미 죽은
     * 토큰을 다음에 다시 내게 되고 서버는 그것을 탈취로 본다(회전).
     */
    const refresh = useCallback(async () => {
        const result = await refreshTokens(tokens?.refreshToken);
        if (result.ok) setTokens(result.tokens);
        return result;
    }, [tokens, setTokens]);

    /**
     * 이 앱이 든 토큰을 버린다. 서버에 알리지 않는다.
     *
     * access 토큰은 서명만 맞으면 통하는 값이라 서버가 막을 방법이 없다. 그래서 짧게 두고,
     * 버리는 것은 들고 있는 쪽의 몫이다. auth 의 로그인 세션까지 끊으려면 부르는 쪽이 이어서
     * 브라우저를 end_session_endpoint 로 보낸다(logoutUrl).
     */
    const logout = useCallback(() => {
        setTokens(null);
    }, [setTokens]);

    const decoded = useMemo(() => decode(tokens?.accessToken), [tokens]);
    const claims = decoded?.payload ?? null;

    const value = useMemo(() => ({
        tokens,
        decoded,
        claims,
        isLoggedIn: Boolean(tokens?.accessToken),
        persist,
        setPersist,
        loginWithCode,
        logout,
        refresh,
    }), [tokens, decoded, claims, persist, setPersist, loginWithCode, logout, refresh]);

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
    const context = useContext(AuthContext);
    if (!context) throw new Error('useAuth 는 AuthProvider 안에서만 쓸 수 있습니다.');
    return context;
}

function readStoredTokens() {
    try {
        return JSON.parse(localStorage.getItem(TOKEN_KEY));
    } catch {
        return null;
    }
}
