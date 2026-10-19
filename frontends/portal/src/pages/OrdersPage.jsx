import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { myOrders } from '../api/order';
import Notice from '../components/Notice';
import { formatPrice } from '../data/products';

const STATUS_LABELS = {
    PLACED: '주문 완료',
};

/**
 * 내 주문 내역.
 *
 * 누구의 주문인지 보내지 않는다. order-service 가 토큰의 sub 로 찾는다. 경로나 쿼리로 보내면
 * 남의 식별자를 적어 넣는 것으로 남의 주문이 열린다.
 */
export default function OrdersPage() {
    const { tokens } = useAuth();
    const [orders, setOrders] = useState(null);
    const [notice, setNotice] = useState(null);

    const accessToken = tokens?.accessToken;

    useEffect(() => {
        async function load() {
            const result = await myOrders(accessToken);
            if (!result.ok) {
                setNotice({ kind: 'err', text: result.message ?? `주문 내역을 불러오지 못했습니다 (${result.status})` });
                return;
            }
            setOrders(result.data);
        }
        if (accessToken) load();
    }, [accessToken]);

    return (
        <div className="page">
            <header className="page-head">
                <h1>주문 내역</h1>
                <p className="lead">최근 주문이 위에 옵니다.</p>
            </header>

            <Notice kind={notice?.kind}>{notice?.text}</Notice>

            <div className="card">
                {orders === null && !notice && <p className="field-hint">불러오는 중입니다.</p>}

                {orders?.length === 0 && (
                    <>
                        <p className="field-hint">아직 주문이 없습니다.</p>
                        <div className="row">
                            <Link className="btn btn-primary" to="/">상품 보러 가기</Link>
                        </div>
                    </>
                )}

                {orders?.length > 0 && (
                    <table className="orders-table">
                        <thead>
                        <tr>
                            <th>주문일</th>
                            <th>상품</th>
                            <th className="num">수량</th>
                            <th className="num">금액</th>
                            <th className="num">상태</th>
                        </tr>
                        </thead>
                        <tbody>
                        {orders.map((order) => (
                            <tr key={order.orderId}>
                                <td>{new Date(order.createdAt).toLocaleString('ko-KR')}</td>
                                <td>
                                    {order.productId
                                        ? <Link to={`/products/${order.productId}`}>{order.productName}</Link>
                                        : order.productName}
                                </td>
                                <td className="num">{order.quantity}</td>
                                <td className="num">{formatPrice(order.amount)}</td>
                                <td className="num">{STATUS_LABELS[order.status] ?? order.status}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                )}
            </div>
        </div>
    );
}
