import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import EndpointSettings from './EndpointSettings';
import RequestLog from './RequestLog';

export default function Layout({ children }) {
    const { isLoggedIn, claims, logout } = useAuth();
    const navigate = useNavigate();

    async function onLogout() {
        await logout();
        navigate('/login');
    }

    return (
        <div className="shell">
            <header className="topbar">
                <div className="topbar-inner">
                    <Link to="/me" className="brand">
                        고객 포털 <span className="badge">realm: portal</span>
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
                                <Link to="/login">로그인</Link>
                                <Link to="/signup">가입</Link>
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
