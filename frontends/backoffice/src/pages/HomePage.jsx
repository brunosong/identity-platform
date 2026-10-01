import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { jwks } from '../api/auth';
import TokenInspector from '../components/TokenInspector';
import Notice from '../components/Notice';

/**
 * 대시보드 - 로그인한 뒤 처음 보는 화면.
 *
 * 위쪽은 한눈에 볼 것(누구로 로그인했나, 언제까지 유효한가), 아래쪽은 그 값들이 어디서 왔는지
 * 직접 열어보는 자리다.
 */
export default function HomePage() {
    const { tokens, claims, refresh } = useAuth();

    const [keys, setKeys] = useState(null);
    const [notice, setNotice] = useState(null);
    const [busy, setBusy] = useState(false);

    const expiresAt = claims?.exp ? new Date(claims.exp * 1000) : null;

    async function onRefresh() {
        setBusy(true);
        setNotice(null);

        const result = await refresh();
        setBusy(false);

        setNotice(result.ok
            ? {
                kind: 'ok',
                text: '새 토큰을 받았습니다. 권한과 리비전이 그 시점 값으로 다시 실립니다 — '
                    + '역할이 바뀌었다면 이제야 반영됩니다.',
            }
            : { kind: 'err', text: result.message ?? `재발급 실패 (${result.status})` });
    }

    async function onLoadJwks() {
        setBusy(true);
        const result = await jwks();
        setBusy(false);

        if (!result.ok) {
            setNotice({ kind: 'err', text: `JWKS 조회 실패 (${result.status})` });
            return;
        }
        setKeys(result.data);
        setNotice({
            kind: 'ok',
            text: `어드민 realm 의 공개키 ${result.data.keys?.length ?? 0}개. `
                + '포털 주소에는 이 키가 없습니다 — 그래서 포털만 상대하는 서비스는 어드민 토큰을 '
                + '검증할 수조차 없습니다.',
        });
    }

    return (
        <div className="page">
            <h1>대시보드</h1>
            <p className="lead">직원이 업무를 보는 앱입니다.</p>

            <Notice kind={notice?.kind}>{notice?.text}</Notice>

            <div className="tiles">
                <div className="tile">
                    <div className="tile-label">로그인 계정</div>
                    <div className="tile-value small">{claims?.sub ?? '-'}</div>
                </div>
                <div className="tile">
                    <div className="tile-label">영역</div>
                    <div className="tile-value">ADMIN</div>
                </div>
                <div className="tile">
                    <div className="tile-label">토큰 만료</div>
                    <div className="tile-value small">
                        {expiresAt ? expiresAt.toLocaleTimeString() : '-'}
                    </div>
                </div>
            </div>

            <div className="card">
                <h2>직원 등록과 역할은 어디서 하나</h2>
                <p className="hint">
                    auth 의 운영 화면에서 MASTER 관리자가 합니다. auth 를 관리하는 것은 MASTER realm 이고,
                    이 앱이 쓰는 직원 realm 은 업무를 보는 사람들의 자리입니다.
                </p>
                <div className="row">
                    <button onClick={onRefresh} disabled={busy}>토큰 재발급</button>
                    <button onClick={onLoadJwks} disabled={busy}>JWKS 보기</button>
                </div>
            </div>

            {keys && (
                <div className="card">
                    <h2>JWKS — 다른 서비스가 검증에 쓸 공개키</h2>
                    <pre className="raw">{JSON.stringify(keys, null, 2)}</pre>
                    <p className="field-hint">
                        <b>어드민 키 하나만 나옵니다.</b> JWKS 는 realm 마다 주소가 다르고,
                        customer-service 는 포털 주소만 알고 있습니다. 그래서 어드민 토큰은 그 서비스의
                        코드가 한 줄도 돌기 전에 서명 검증에서 죽습니다 — 해당 <code>kid</code> 의
                        공개키가 아예 없기 때문입니다.
                    </p>
                </div>
            )}

            <TokenInspector label="access 토큰" token={tokens?.accessToken} />
            <TokenInspector label="refresh 토큰" token={tokens?.refreshToken} kind="refresh" />
        </div>
    );
}
