import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import * as authApi from '../api/auth';
import { exchangeCode, forgetLoggedIn, rememberLoggedIn } from '../api/authorize';
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
 * 실습 편의로 localStorage 를 쓰는 스위치를 두되 <b>기본은 꺼둔다</b>. 켜보면 편해지는 만큼
 * 무엇을 내주는지도 같이 보게 하려는 것이다.
 *
 * (제대로 하려면 BFF 를 두고 토큰은 서버가 들고, 브라우저는 httpOnly 쿠키만 받는다.
 *  그러면 편의와 안전을 둘 다 갖는다. 이 앱에는 BFF 가 없다.)
 */

const AuthContext = createContext(null);

const PERSIST_FLAG = 'portal.persistTokens';
const TOKEN_KEY = 'portal.tokens';

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

    const login = useCallback(async ({ loginId, password }) => {
        const result = await authApi.login({ loginId, password });
        if (result.ok) setTokens(result.data.tokens);
        return result;
    }, [setTokens]);

    /**
     * 구글에서 받은 code 로 로그인한다.
     *
     * 비밀번호 로그인과 결과가 같다 - 응답 본문에 우리 토큰이 온다. 앞의 과정만 다를 뿐,
     * 토큰을 손에 쥐는 방식은 하나다.
     */
    const loginWithSocial = useCallback(async (code) => {
        const result = await authApi.loginWithSocial(code);
        if (result.ok) setTokens(result.data.tokens);
        return result;
    }, [setTokens]);

    /**
     * 우리 인증 서버에서 받은 code 로 로그인한다.
     *
     * 다른 두 방식과 끝이 같다 - 토큰을 받아 여기 담는다. 다른 것은 앞의 과정뿐이다.
     * 이 방식에서는 비밀번호가 이 앱을 거치지 않았다.
     */
    const loginWithCode = useCallback(async (code) => {
        const result = await exchangeCode(code);
        if (result.ok) {
            setTokens(result.tokens);
            // 다음에 새로고침으로 토큰이 날아가도 조용히 되찾아 올 수 있다는 표시다.
            // 토큰이 아니라 깃발 하나라 localStorage 에 둬도 잃을 것이 없다.
            rememberLoggedIn();
        }
        return result;
    }, [setTokens]);

    const refresh = useCallback(async () => {
        if (!tokens?.refreshToken) return { ok: false, status: 0, message: '리프레시 토큰이 없습니다.' };
        const result = await authApi.refresh(tokens.refreshToken);
        if (result.ok) setTokens(result.data.tokens);
        return result;
    }, [tokens, setTokens]);

    const logout = useCallback(async () => {
        const result = tokens?.accessToken
            ? await authApi.logout(tokens.accessToken)
            : { ok: true, status: 0 };
        // 서버 응답과 무관하게 버린다. 단일 세션을 켜지 않았다면 서버는 이 토큰을 막을 방법이 없고,
        // 폐기는 우리 몫이다.
        setTokens(null);
        // 깃발도 내린다. 안 내리면 로그아웃한 사람이 새 탭을 열 때마다 헛왕복을 한 번씩 한다.
        forgetLoggedIn();
        return result;
    }, [tokens, setTokens]);

    const decoded = useMemo(() => decode(tokens?.accessToken), [tokens]);

    const value = useMemo(() => ({
        tokens,
        decoded,
        claims: decoded?.payload ?? null,
        isLoggedIn: Boolean(tokens?.accessToken),
        persist,
        setPersist,
        login,
        loginWithSocial,
        loginWithCode,
        logout,
        refresh,
    }), [tokens, decoded, persist, setPersist, login, loginWithSocial, loginWithCode, logout, refresh]);

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
