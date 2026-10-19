import { Fragment, useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { placeOrder } from '../api/order';
import Notice from '../components/Notice';
import ProductThumb from '../components/ProductThumb';
import { findProduct, formatPrice } from '../data/products';

/** 한 번에 고를 수 있는 최대 수량. 재고가 이보다 적으면 재고까지만 고른다. */
const MAX_QUANTITY = 10;

/**
 * 상품 상세.
 *
 * <b>로그인 없이 볼 수 있다.</b> 상품 설명은 누구에게나 같은 값이라 신원을 물을 이유가 없다.
 * 신원이 필요해지는 곳은 주문 버튼부터다. 그 경계가 이 화면에서 눈에 보이는 것이 요점이다.
 *
 * 주문은 상품 번호와 수량만 보낸다. 가격은 order-service 가 정한다. 주문이 끝나면 남은 수량을
 * 다시 읽는다. 내가 산 만큼, 그리고 그사이 다른 사람이 산 만큼 줄어 있다.
 */
export default function ProductDetailPage() {
    const { id } = useParams();
    const { isLoggedIn, tokens } = useAuth();
    const [product, setProduct] = useState(null);
    const [loaded, setLoaded] = useState(false);
    const [quantity, setQuantity] = useState(1);
    const [notice, setNotice] = useState(null);
    const [busy, setBusy] = useState(false);

    const load = useCallback(async () => {
        const result = await findProduct(id);
        setLoaded(true);
        if (!result.ok) {
            setNotice({ kind: 'err', text: result.message ?? `상품을 불러오지 못했습니다 (${result.status})` });
            return;
        }
        setProduct(result.product ?? null);
    }, [id]);

    useEffect(() => {
        load();
    }, [load]);

    async function onOrder() {
        setBusy(true);
        setNotice(null);

        const result = await placeOrder(tokens?.accessToken, { productId: product.id, quantity });
        setBusy(false);

        if (!result.ok) {
            setNotice({ kind: 'err', text: result.message ?? `주문하지 못했습니다 (${result.status})` });
            load();
            return;
        }

        setNotice({
            kind: 'ok',
            text: `주문했습니다. ${result.data.quantity}개, ${formatPrice(result.data.amount)}.`,
        });
        setQuantity(1);
        load();
    }

    if (!loaded) {
        return <div className="page narrow"><p className="lead">불러오는 중입니다.</p></div>;
    }

    if (!product) {
        return (
            <div className="page narrow">
                <Notice kind={notice?.kind}>{notice?.text}</Notice>
                <div className="page-head">
                    <h1>없는 상품입니다</h1>
                    <p className="lead">주소가 바뀌었거나 판매가 끝난 상품일 수 있습니다.</p>
                </div>
                <Link className="btn btn-ghost" to="/">목록으로</Link>
            </div>
        );
    }

    const soldOut = product.stock === 0;
    const quantityChoices = Array.from(
        { length: Math.min(product.stock, MAX_QUANTITY) },
        (_, index) => index + 1,
    );

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
                        <>
                            {!soldOut && (
                                <div className="order-line">
                                    <label htmlFor="o-qty">수량</label>
                                    <select id="o-qty" value={quantity}
                                            onChange={(e) => setQuantity(Number(e.target.value))}>
                                        {quantityChoices.map((count) => (
                                            <option key={count} value={count}>{count}개</option>
                                        ))}
                                    </select>
                                    <span className="order-total">
                                        합계 {formatPrice(product.price * quantity)}
                                    </span>
                                </div>
                            )}
                            <button
                                className="btn btn-primary btn-block"
                                disabled={soldOut || busy}
                                onClick={onOrder}
                            >
                                {soldOut ? '품절' : '주문하기'}
                            </button>
                            {notice?.kind === 'ok' && (
                                <Link className="btn btn-ghost btn-block" to="/orders">주문 내역 보기</Link>
                            )}
                        </>
                    ) : (
                        <>
                            {/* state 는 로그인 뒤 돌아올 자리다. auth 에는 가지 않는다. */}
                            <Link
                                className="btn btn-primary btn-block"
                                to="/login"
                                state={{ from: `/products/${product.id}` }}
                            >
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
