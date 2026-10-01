import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { logoutUrl } from '../api/authorize';
import EndpointSettings from './EndpointSettings';
import RequestLog from './RequestLog';

/**
 * 관리자 콘솔의 뼈대.
 *
 * 로그인했을 때만 사이드바가 선다. 로그인 전에는 볼 수 있는 화면이 로그인과 가입뿐이라
 * 메뉴를 세울 이유가 없고, 세우면 눌러봐야 전부 로그인으로 튕긴다.
 */
export default function Layout({ children }) {
    const { isLoggedIn, claims, logout } = useAuth();

    /**
     * 토큰을 버리고 auth 의 로그인 세션도 끊는다.
     *
     * 앱에서 토큰만 버리면 같은 realm 의 다른 앱에서는 여전히 로그인 상태다. 브라우저를 auth 로
     * 보내야 세션 쿠키가 지워진다.
     */
    function onLogout() {
        logout();
        window.location.href = logoutUrl();
    }

    if (!isLoggedIn) {
        return (
            <div className="shell plain">
                <main className="content">{children}</main>
                <EndpointSettings />
                <RequestLog />
            </div>
        );
    }

    return (
        <div className="shell">
            <aside className="sidebar">
                <Link to="/" className="brand">
                    직원 관리자
                    <span className="badge">realm: admin</span>
                </Link>

                <nav className="sidenav">
                    <NavLink to="/" end>대시보드</NavLink>
                </nav>

                <div className="sidebar-foot">
                    BrunoSong Identity<br />
                    인증은 auth-service 가 맡습니다
                </div>
            </aside>

            <div>
                <header className="topbar">
                    <div className="topbar-inner">
                        <span className="who">{claims?.sub}</span>
                        <nav className="topnav">
                            <button className="link-button" onClick={onLogout}>로그아웃</button>
                        </nav>
                    </div>
                </header>

                <main className="content">{children}</main>
            </div>

            <EndpointSettings />
            <RequestLog />
        </div>
    );
}
