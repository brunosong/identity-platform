import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import * as authApi from '../api/auth';
import { setAccessTokenRefresher } from '../api/http';
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
        if (result.ok) setTokens(result.tokens);
        return result;
    }, [setTokens]);

    // 갱신 함수가 클로저에 낡은 토큰을 잡지 않게 한다. 자동 갱신은 언제 불릴지 모른다.
    const tokensRef = useRef(tokens);
    useEffect(() => { tokensRef.current = tokens; }, [tokens]);

    /** 재발급이 도는 동안의 Promise. 동시에 여러 요청이 401 을 받아도 갱신은 한 번이다. */
    const refreshing = useRef(null);

    /**
     * 재발급. 받은 쌍을 <b>통째로</b> 갈아끼운다. access 만 챙기고 refresh 를 두면, 이미 죽은
     * 토큰을 다음에 다시 내게 되고 서버는 그것을 탈취로 본다(회전).
     *
     * <b>묶어서 한 번만 부른다.</b> 화면이 프로필과 권한을 같이 부르면 401 이 둘 온다. 각자
     * 재발급하면 진 쪽이 이미 쓴 토큰을 내게 되어 계보가 끊긴다. 그래서 같은 Promise 를 돌려준다.
     */
    const refresh = useCallback(() => {
        if (!refreshing.current) {
            refreshing.current = refreshTokens(tokensRef.current?.refreshToken)
                .then((result) => {
                    refreshing.current = null;
                    if (result.ok) setTokens(result.tokens);
                    return result;
                });
        }
        return refreshing.current;
    }, [setTokens]);

    /**
     * 401 을 만난 요청이 부를 자리를 http.js 에 등록한다.
     *
     * 돌려주는 것은 새 access 토큰이다. 되살리지 못하면 토큰을 버린다 - 안 버리면 죽은 토큰을
     * 들고 오류 화면 앞에 앉아 있게 된다. 버리면 RequireAuth 가 받아서 로그인으로 보낸다.
     */
    useEffect(() => {
        setAccessTokenRefresher(async () => {
            const result = await refresh();
            if (result.ok) return result.tokens.accessToken;
            setTokens(null);
            return null;
        });
    }, [refresh, setTokens]);

    const logout = useCallback(async () => {
        const result = tokens?.accessToken
            ? await authApi.logout(tokens.accessToken)
            : { ok: true, status: 0 };
        // 서버 응답과 무관하게 버린다. 단일 세션을 켜지 않았다면 서버는 이 토큰을 막을 방법이 없고,
        // 폐기는 우리 몫이다.
        setTokens(null);
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
