import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import * as authApi from '../api/auth';
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
 * <h3>사라진 것을 대신 되살리지 않는다</h3>
 * 새로고침하면 로그아웃이고, access 가 만료되면 요청이 401 로 끝난다. 둘 다 뒤에서 조용히
 * 메워주지 않는다. <b>무엇이 언제 죽는지 눈에 보이게 두려는 것이다.</b> 재발급은 마이페이지의
 * 버튼으로만 돈다.
 *
 * <p>편의를 되살리려면 둘이 한 벌로 온다. 401 자동 재시도와 <b>재발급 묶기</b>다. 회전이 붙어
 * 있어서 묶지 않으면 동시에 401 을 받은 요청 둘이 각자 재발급하고, 진 쪽이 이미 쓴 토큰을 내밀어
 * 계보가 통째로 끊긴다.
 *
 * (제대로 하려면 BFF 를 두고 토큰은 서버가 들고, 브라우저는 httpOnly 쿠키만 받는다.
 *  그러면 편의와 안전을 둘 다 갖는다. 이 앱에는 BFF 가 없다.)
 */

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
    const [tokens, setTokens] = useState(null);

    const login = useCallback(async ({ loginId, password }) => {
        const result = await authApi.login({ loginId, password });
        if (result.ok) setTokens(result.data.tokens);
        return result;
    }, []);

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
    }, []);

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
    }, []);

    /**
     * 재발급. 받은 쌍을 <b>통째로</b> 갈아끼운다. access 만 챙기고 refresh 를 두면, 이미 죽은
     * 토큰을 다음에 다시 내게 되고 서버는 그것을 탈취로 본다(회전).
     */
    const refresh = useCallback(async () => {
        const result = await refreshTokens(tokens?.refreshToken);
        if (result.ok) setTokens(result.tokens);
        return result;
    }, [tokens]);

    const logout = useCallback(async () => {
        const result = tokens?.accessToken
            ? await authApi.logout(tokens.accessToken)
            : { ok: true, status: 0 };
        // 서버 응답과 무관하게 버린다. 단일 세션을 켜지 않았다면 서버는 이 토큰을 막을 방법이 없고,
        // 폐기는 우리 몫이다.
        setTokens(null);
        return result;
    }, [tokens]);

    const decoded = useMemo(() => decode(tokens?.accessToken), [tokens]);

    const value = useMemo(() => ({
        tokens,
        decoded,
        claims: decoded?.payload ?? null,
        isLoggedIn: Boolean(tokens?.accessToken),
        login,
        loginWithSocial,
        loginWithCode,
        logout,
        refresh,
    }), [tokens, decoded, login, loginWithSocial, loginWithCode, logout, refresh]);

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
    const context = useContext(AuthContext);
    if (!context) throw new Error('useAuth 는 AuthProvider 안에서만 쓸 수 있습니다.');
    return context;
}
