import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import ProductThumb from '../components/ProductThumb';
import { formatPrice, listProducts } from '../data/products';

/**
 * 메인 화면.
 *
 * 로그인하지 않아도 들어올 수 있다. 상품을 보는 데 신원이 필요하지 않기 때문이다. 로그인이
 * 필요해지는 곳은 주문부터이고, 그 경계는 상세 화면에서 드러난다.
 *
 * 상품은 아직 프론트에 박혀 있다(data/products.js). 상품 API 가 없다.
 */
export default function HomePage() {
    const { isLoggedIn, claims } = useAuth();
    const products = listProducts();

    return (
        <div className="page">
            <section className="hero hero-compact">
                <p className="hero-eyebrow">PORTAL</p>
                <h1 className="hero-title">책상 위의 것들</h1>
                <p className="hero-sub">
                    하나의 계정으로 주문하고, 배송과 프로필을 한곳에서 관리하세요.
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

            <section className="section">
                <div className="section-head">
                    <h2>전체 상품</h2>
                    <span className="section-count">{products.length}개</span>
                </div>

                <div className="products">
                    {products.map((product) => (
                        <ProductCard key={product.id} product={product} />
                    ))}
                </div>
            </section>

            <section className="section">
                <div className="section-head">
                    <h2>내 계정</h2>
                </div>

                <div className="services">
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
                </div>
            </section>
        </div>
    );
}

function ProductCard({ product }) {
    const soldOut = product.stock === 0;

    return (
        <Link className={`product ${soldOut ? 'sold-out' : ''}`} to={`/products/${product.id}`}>
            <ProductThumb product={product} />
            <div className="product-body">
                <span className="product-category">{product.category}</span>
                <h3>{product.name}</h3>
                <p className="product-summary">{product.summary}</p>
                <div className="product-foot">
                    <strong className="product-price">{formatPrice(product.price)}</strong>
                    {soldOut && <span className="pill danger">품절</span>}
                </div>
            </div>
        </Link>
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
