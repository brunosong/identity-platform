import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { logoutUrl } from '../api/authorize';
import EndpointSettings from './EndpointSettings';
import RequestLog from './RequestLog';

export default function Layout({ children }) {
    const { isLoggedIn, canManage, claims, logout } = useAuth();

    /**
     * 토큰을 버리고 auth 의 로그인 세션도 끊는다.
     *
     * 앱에서 토큰만 버리면 같은 realm 의 다른 앱에서는 여전히 로그인 상태다. 브라우저를 auth 로
     * 보내야 세션 쿠키가 지워진다.
     */
    async function onLogout() {
        await logout();
        window.location.href = logoutUrl();
    }

    return (
        <div className="shell">
            <header className="topbar">
                <div className="topbar-inner">
                    <Link to="/" className="brand">
                        직원 관리자 <span className="badge">realm: admin</span>
                    </Link>

                    <nav className="topnav">
                        {isLoggedIn ? (
                            <>
                                <span className="who">{claims?.sub}</span>
                                <NavLink to="/">홈</NavLink>
                                {/* 권한이 없으면 메뉴를 감춘다. 편의일 뿐이고 방어는 서버가 한다. */}
                                {canManage && <NavLink to="/users/new">직원 등록</NavLink>}
                                {canManage && <NavLink to="/rbac">인가 정책</NavLink>}
                                <button className="link-button" onClick={onLogout}>로그아웃</button>
                            </>
                        ) : (
                            <>
                                <NavLink to="/login">로그인</NavLink>
                                <NavLink to="/signup">가입</NavLink>
                            </>
                        )}
                    </nav>
                </div>
            </header>

            <main className="content">{children}</main>

            <EndpointSettings />
            <RequestLog />
        </div>
    );
}
