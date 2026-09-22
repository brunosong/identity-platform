import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { logoutUrl } from '../api/authorize';
import EndpointSettings from './EndpointSettings';
import RequestLog from './RequestLog';

export default function Layout({ children }) {
    const { isLoggedIn, claims, logout } = useAuth();

    /**
     * 토큰을 버리고 auth 의 로그인 세션도 끊는다.
     *
     * 앱에서 토큰만 버리면 다른 앱에서는 여전히 로그인 상태다. 브라우저를 auth 로 보내야
     * 세션 쿠키가 지워지고, 그래야 어느 앱에서든 로그인 화면이 다시 뜬다.
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
                        <span className="brand-mark">P</span>
                        <span className="brand-name">PORTAL</span>
                    </Link>

                    <nav className="topnav">
                        {isLoggedIn ? (
                            <>
                                <span className="who">{claims?.sub?.slice(0, 8)}…</span>
                                <Link to="/me">마이페이지</Link>
                                <button className="link-button" onClick={onLogout}>로그아웃</button>
                            </>
                        ) : (
                            <>
                                <Link to="/signup">회원가입</Link>
                                <Link className="btn btn-sm btn-primary" to="/login">로그인</Link>
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
