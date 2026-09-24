import { Fragment, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import Notice from '../components/Notice';
import ProductThumb from '../components/ProductThumb';
import { findProduct, formatPrice } from '../data/products';

/**
 * 상품 상세.
 *
 * <b>로그인 없이 볼 수 있다.</b> 상품 설명은 누구에게나 같은 값이라 신원을 물을 이유가 없다.
 * 신원이 필요해지는 곳은 주문 버튼부터다. 그 경계가 이 화면에서 눈에 보이는 것이 요점이다.
 *
 * 아래 주문 버튼은 아직 주문을 넣지 못한다. order-service 에 주문을 만드는 API 가 없다.
 * 그래서 로그인 여부에 따라 버튼이 갈리는 데까지만 한다.
 */
export default function ProductDetailPage() {
    const { id } = useParams();
    const { isLoggedIn } = useAuth();
    const [notice, setNotice] = useState(null);

    const product = findProduct(id);

    if (!product) {
        return (
            <div className="page narrow">
                <div className="page-head">
                    <h1>없는 상품입니다</h1>
                    <p className="lead">주소가 바뀌었거나 판매가 끝난 상품일 수 있습니다.</p>
                </div>
                <Link className="btn btn-ghost" to="/">목록으로</Link>
            </div>
        );
    }

    const soldOut = product.stock === 0;

    return (
        <div className="page">
            <Link className="back-link" to="/">목록으로</Link>

            <div className="detail">
                <ProductThumb product={product} size="detail" />

                <div className="detail-info">
                    <span className="product-category">{product.category}</span>
                    <h1>{product.name}</h1>
                    <p className="detail-summary">{product.summary}</p>

                    <div className="detail-price">
                        <strong>{formatPrice(product.price)}</strong>
                        {soldOut
                            ? <span className="pill danger">품절</span>
                            : <span className="pill">남은 수량 {product.stock}개</span>}
                    </div>

                    <Notice kind={notice?.kind}>{notice?.text}</Notice>

                    {isLoggedIn ? (
                        <button
                            className="btn btn-primary btn-block"
                            disabled={soldOut}
                            onClick={() => setNotice({
                                kind: 'info',
                                text: '주문을 넣을 API 가 아직 없습니다. order-service 는 주문 조회만 합니다.',
                            })}
                        >
                            주문하기
                        </button>
                    ) : (
                        <>
                            <Link className="btn btn-primary btn-block" to="/login">
                                로그인하고 주문하기
                            </Link>
                            <p className="field-hint">
                                여기까지는 로그인 없이 볼 수 있습니다. 주문은 누가 시켰는지를
                                남겨야 해서 신원이 필요합니다.
                            </p>
                        </>
                    )}
                </div>
            </div>

            <section className="card">
                <h2>상품 설명</h2>
                {product.description.map((paragraph) => (
                    <p key={paragraph.slice(0, 20)} className="detail-text">{paragraph}</p>
                ))}
            </section>

            <section className="card">
                <h2>사양</h2>
                <div className="kv">
                    {Object.entries(product.specs).map(([key, value]) => (
                        <Fragment key={key}>
                            <span>{key}</span>
                            <span>{value}</span>
                        </Fragment>
                    ))}
                </div>
            </section>
        </div>
    );
}
