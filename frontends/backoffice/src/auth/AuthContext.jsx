import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import * as authApi from '../api/auth';
import { exchangeCode } from '../api/authorize';
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
 * <p><b>이 앱에서는 그 값이 더 크다.</b> 여기 토큰에는 계정을 만들고 역할을 배정할 수 있는
 * 권한이 실려 있다. 고객 토큰이 새면 그 사람 프로필이 새지만, 이 토큰이 새면 직원이 만들어진다.
 *
 * <h3>권한은 토큰이 아니라 서버에 묻는다</h3>
 * 전에는 토큰의 `resource_access` 칸을 읽었다. 토큰에서 인가 클레임을 걷어내면서 그 자리가
 * 없어졌고, 지금은 로그인 뒤 `/my-permissions` 를 한 번 부른다. auth 가 DB 를 조회해 답한다.
 *
 * <p>덤으로 정확해졌다. 토큰에 실려 있을 때는 발급 시점의 값이라 역할을 새로 받아도 재발급
 * 전에는 보이지 않았는데, 이제 부를 때마다 최신이다.
 *
 * <p>어차피 화면을 여는 기준일 뿐이다. 실제 방어는 서버가 한다.
 */

const AuthContext = createContext(null);

/** 관리 기능을 여는 역할. 서버도 같은 코드를 요구한다(authorization.manage-permission). */
export const MANAGE_PERMISSION = 'AUTHZ_MANAGE';

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
        return result;
    }, [tokens, setTokens]);

    const decoded = useMemo(() => decode(tokens?.accessToken), [tokens]);
    const claims = decoded?.payload ?? null;

    // 토큰에 권한이 없으므로 서버에 묻는다. 토큰이 바뀌면(로그인, 재발급) 다시 읽는다.
    const [permissions, setPermissions] = useState([]);
    useEffect(() => {
        if (!tokens?.accessToken) {
            setPermissions([]);
            return;
        }
        let cancelled = false;
        authApi.myPermissions(tokens.accessToken).then((result) => {
            if (!cancelled) setPermissions(result.ok ? (result.data.permissions ?? []) : []);
        });
        return () => { cancelled = true; };
    }, [tokens]);

    const value = useMemo(() => ({
        tokens,
        decoded,
        claims,
        permissions,
        isLoggedIn: Boolean(tokens?.accessToken),
        // 화면을 여는 기준일 뿐이다. 실제 방어는 서버가 한다 — 여기를 고쳐도 API 는 403 이다.
        canManage: permissions.includes(MANAGE_PERMISSION),
        persist,
        setPersist,
        loginWithCode,
        logout,
        refresh,
    }), [tokens, decoded, claims, permissions, persist, setPersist, loginWithCode, logout, refresh]);

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
