import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

/**
 * 메인 화면.
 *
 * 로그인하지 않아도 들어올 수 있다. 서비스가 무엇인지 보여주고 로그인으로 안내하는 자리라,
 * 보호할 것이 없다.
 *
 * 아래 서비스 카드는 실제로 이 저장소에 있는 서비스들이다. 프로필은 customer-service,
 * 주문은 order-service 가 답한다. 둘 다 auth 가 발급한 토큰을 자기가 검증한다.
 */
export default function HomePage() {
    const { isLoggedIn, claims } = useAuth();

    return (
        <div className="page">
            <section className="hero">
                <p className="hero-eyebrow">PORTAL</p>
                <h1 className="hero-title">하나의 계정으로<br />모든 서비스를</h1>
                <p className="hero-sub">
                    주문, 배송, 프로필을 한곳에서 관리하세요.
                </p>

                <div className="hero-actions">
                    {isLoggedIn ? (
                        <>
                            <Link className="btn btn-primary" to="/me">마이페이지</Link>
                            <span className="hero-who">
                                {claims?.sub?.slice(0, 8)}… 으로 로그인됨
                            </span>
                        </>
                    ) : (
                        <>
                            <Link className="btn btn-primary" to="/login">로그인</Link>
                            <Link className="btn btn-ghost" to="/signup">회원가입</Link>
                        </>
                    )}
                </div>
            </section>

            <section className="services">
                <ServiceCard
                    title="내 프로필"
                    body="이름과 연락처를 확인하고 바꿉니다."
                    to="/me"
                    locked={!isLoggedIn}
                />
                <ServiceCard
                    title="주문 내역"
                    body="주문과 배송 상태를 봅니다."
                    to="/me"
                    locked={!isLoggedIn}
                />
                <ServiceCard
                    title="계정 보안"
                    body="로그인 수단과 세션을 관리합니다."
                    to="/me"
                    locked={!isLoggedIn}
                />
            </section>
        </div>
    );
}

/** 로그인 전에는 잠긴 채로 보인다. 감추지 않는 것은 무엇이 있는지는 알려주려는 것이다. */
function ServiceCard({ title, body, to, locked }) {
    const target = locked ? '/login' : to;

    return (
        <Link className={`service ${locked ? 'locked' : ''}`} to={target}>
            <h3>{title}</h3>
            <p>{body}</p>
            <span className="service-more">{locked ? '로그인 필요' : '바로가기'}</span>
        </Link>
    );
}
