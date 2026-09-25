import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth, MANAGE_PERMISSION } from '../auth/AuthContext';
import { jwks, myPermissions, trySocialHere } from '../api/auth';
import TokenInspector from '../components/TokenInspector';
import Notice from '../components/Notice';

/**
 * 대시보드 - 로그인한 뒤 처음 보는 화면.
 *
 * 위쪽은 운영자가 한눈에 볼 것(누구로 로그인했나, 무엇을 할 수 있나, 언제까지 유효한가),
 * 아래쪽은 그 값들이 어디서 왔는지 직접 열어보는 자리다.
 */
export default function HomePage() {
    const { tokens, claims, permissions, canManage, refresh } = useAuth();

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

    async function onLoadPermissions() {
        setBusy(true);
        const result = await myPermissions(tokens.accessToken);
        setBusy(false);

        if (!result.ok) {
            setNotice({ kind: 'err', text: `권한 조회 실패 (${result.status})` });
            return;
        }
        setNotice({
            kind: 'ok',
            text: `realm ${result.data.realm ?? '(없음)'} · `
                + `${result.data.permissions.join(', ') || '권한 없음'} — `
                + '이 값은 서버가 토큰을 열어 읽어준 것입니다.',
        });
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

    async function onTrySocial() {
        setBusy(true);
        const result = await trySocialHere();
        setBusy(false);

        setNotice({
            kind: result.ok ? 'err' : 'ok',
            text: `(${result.status}) ${result.message ?? ''} — `
                + '소셜은 최초 로그인에 신원을 새로 만듭니다(JIT). 직원 realm 에서 열려 있으면 '
                + '아무나 소셜 로그인만으로 직원 신원을 만들 수 있어서, 고객 realm 에서만 엽니다.',
        });
    }

    return (
        <div className="page">
            <h1>대시보드</h1>
            <p className="lead">직원 계정과 인가 정책을 관리합니다.</p>

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
                    <div className="tile-label">관리 권한</div>
                    <div className="tile-value">{canManage ? '있음' : '없음'}</div>
                </div>
                <div className="tile">
                    <div className="tile-label">토큰 만료</div>
                    <div className="tile-value small">
                        {expiresAt ? expiresAt.toLocaleTimeString() : '-'}
                    </div>
                </div>
            </div>

            {canManage ? (
                <div className="card">
                    <h2>바로가기</h2>
                    <div className="row" style={{ marginTop: 0 }}>
                        <Link className="button-like" to="/users/new">직원 등록</Link>
                        <Link className="button-like" to="/rbac">인가 정책</Link>
                    </div>
                </div>
            ) : (
                <div className="card">
                    <h2>관리 기능이 보이지 않는 이유</h2>
                    <p className="hint">
                        <code>{MANAGE_PERMISSION}</code> 권한이 없습니다. 관리자가 역할을 부여한 뒤
                        <b> 토큰 재발급</b>을 받아야 보입니다 — 권한은 토큰이 발급될 때 실리기 때문에,
                        지금 들고 있는 토큰에는 역할 변경이 반영되지 않습니다.
                    </p>
                </div>
            )}

            <div className="card">
                <div className="card-head">
                    <h2>내 권한</h2>
                    <span className={`pill ${canManage ? 'ok' : ''}`}>
                        {canManage ? MANAGE_PERMISSION : '관리 권한 없음'}
                    </span>
                </div>
                <div>
                    {permissions.length === 0
                        ? <em className="hint">없음</em>
                        : permissions.map((p) => <span className="chip" key={p}>{p}</span>)}
                </div>
                <p className="field-hint">
                    토큰에는 권한이 실리지 않습니다. 이 목록은 서버에 물어 받은 값입니다 —
                    그래서 역할이 바뀌면 다음 조회부터 바로 반영됩니다.
                </p>

                <div className="row">
                    <button onClick={onLoadPermissions} disabled={busy}>다시 조회</button>
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

            <div className="card muted-card">
                <h2>realm 격리 확인 — 소셜 로그인</h2>
                <p className="field-hint">
                    어드민 realm 에서 소셜 로그인을 시도합니다. 404 여야 합니다.
                </p>
                <div className="row">
                    <button onClick={onTrySocial} disabled={busy}>어드민 realm 에서 소셜 로그인 시도</button>
                </div>
            </div>
        </div>
    );
}
