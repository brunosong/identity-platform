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
