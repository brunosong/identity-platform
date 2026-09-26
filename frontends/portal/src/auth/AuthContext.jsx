import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import * as authApi from '../api/auth';
import { setAccessTokenRefresher } from '../api/http';
import { exchangeCode, refreshTokens } from '../api/authorize';
import { decode } from '../api/jwt';

/**
 * 로그인 상태를 들고 있는 곳.
 *
 * <h3>토큰은 메모리에만 둔다</h3>
 * 새로고침하면 사라진다 — 불편하지만 그게 정직한 기본값이다.
 *
 * 쿠키의 {@code httpOnly} 를 포기하고 본문으로 토큰을 받기로 한 순간, 토큰은 스크립트가 읽을 수
 * 있는 자리에 놓인다. XSS 하나면 털린다는 뜻이다. 그때 남은 완화책은 "오래 남는 자리에 두지 않기"
 * 뿐이다. {@code localStorage} 는 탭을 닫아도, 브라우저를 껐다 켜도 남는다.
 *
 * <h3>새로고침은 되살리지 않고, 만료는 되살린다</h3>
 * 새로고침하면 로그아웃이다. 토큰이 메모리에만 있으니 되살릴 것이 없다.
 *
 * <p>access 만료는 다르다. 401 을 만난 요청이 여기 등록된 재발급을 부르고, 새 토큰으로 한 번
 * 다시 보낸다({@code api/http.js}). 사람이 보기에는 아무 일도 안 일어난다.
 *
 * <p><b>재발급은 묶어서 한 번만 나간다.</b> 회전이 붙어 있어서 묶지 않으면 동시에 401 을 받은
 * 요청 둘이 각자 재발급하고, 진 쪽이 이미 쓴 토큰을 내밀어 계보가 통째로 끊긴다.
 *
 * (제대로 하려면 BFF 를 두고 토큰은 서버가 들고, 브라우저는 httpOnly 쿠키만 받는다.
 *  그러면 편의와 안전을 둘 다 갖는다. 이 앱에는 BFF 가 없다.)
 */

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
    const [tokens, setTokensState] = useState(null);

    /**
     * 화면이 보는 상태와 재발급이 읽는 값을 <b>같이</b> 갱신한다.
     *
     * ref 갱신을 효과로 미루면 안 된다. 재발급이 막 끝난 직후에 또 401 이 오면, 그 요청은 React 가
     * 상태를 반영하기 전에 ref 를 읽는다. 그러면 방금 회전으로 죽은 refresh 토큰을 다시 내밀게 되고,
     * 서버는 그것을 탈취로 보고 계보를 통째로 끊는다. 화면은 재발급 실패로 멈춰 선다.
     */
    const tokensRef = useRef(null);
    const setTokens = useCallback((next) => {
        tokensRef.current = next;
        setTokensState(next);
    }, []);

    /**
     * auth 에서 받은 code 로 로그인한다. 토큰을 받는 길은 이것 하나다.
     *
     * 비밀번호든 구글이든 사람을 확인하는 일은 auth 의 로그인 화면이 했다. 이 앱이 받는 것은
     * 그 결과로 나온 code 이고, 그것을 토큰으로 바꿔 여기 담는다.
     */
    const loginWithCode = useCallback(async (code) => {
        const result = await exchangeCode(code);
        if (result.ok) setTokens(result.tokens);
        return result;
    }, [setTokens]);

    /** 재발급이 도는 동안의 Promise. 동시에 여러 요청이 401 을 받아도 재발급은 한 번이다. */
    const refreshing = useRef(null);

    /**
     * 재발급. 받은 쌍을 <b>통째로</b> 갈아끼운다. access 만 챙기고 refresh 를 두면, 이미 죽은
     * 토큰을 다음에 다시 내게 되고 서버는 그것을 탈취로 본다(회전).
     *
     * 도는 중에 또 불리면 같은 Promise 를 돌려준다. 그래서 회전은 한 번만 일어난다.
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
     * 돌려주는 것은 새 access 토큰이다. 되살리지 못하면 토큰을 버린다. 안 버리면 죽은 토큰을
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
        loginWithCode,
        logout,
        refresh,
    }), [tokens, decoded, loginWithCode, logout, refresh]);

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
    const context = useContext(AuthContext);
    if (!context) throw new Error('useAuth 는 AuthProvider 안에서만 쓸 수 있습니다.');
    return context;
}
