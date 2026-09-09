import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { jwks, myPermissions } from '../api/auth';
import { myProfile, saveMyProfile } from '../api/customer';
import TokenInspector from '../components/TokenInspector';
import Notice from '../components/Notice';

/**
 * 마이페이지 — 로그인 뒤에 확인할 수 있는 것을 한 화면에 모은다.
 *
 * 이 화면의 요점은 <b>두 서버를 부른다</b>는 것이다. 프로필은 customer-service 에서 오고,
 * 권한은 auth-service 에서 온다. 같은 토큰 하나로 둘 다 부른다.
 *
 * 그리고 customer-service 를 부를 때 auth 는 등장하지 않는다 — 아래 요청 로그에서 확인할 수 있다.
 * customer-service 가 공개키(JWKS)로 직접 서명을 검증하기 때문이다.
 */
export default function MyPage() {
    const { tokens, claims, refresh, logout } = useAuth();

    const [profile, setProfile] = useState(null);
    const [profileMissing, setProfileMissing] = useState(false);
    const [form, setForm] = useState({ name: '', phoneNumber: '', email: '' });
    const [permissions, setPermissions] = useState(null);
    const [keys, setKeys] = useState(null);
    const [notice, setNotice] = useState(null);
    const [busy, setBusy] = useState(false);

    const accessToken = tokens?.accessToken;

    const loadProfile = useCallback(async () => {
        const result = await myProfile(accessToken);
        if (result.status === 404) {
            setProfileMissing(true);
            setProfile(null);
            return;
        }
        if (!result.ok) {
            setNotice({ kind: 'err', text: result.message ?? `프로필 조회 실패 (${result.status})` });
            return;
        }
        setProfileMissing(false);
        setProfile(result.data);
        setForm({
            name: result.data.name ?? '',
            phoneNumber: result.data.phoneNumber ?? '',
            email: result.data.email ?? '',
        });
    }, [accessToken]);

    useEffect(() => {
        if (accessToken) loadProfile();
    }, [accessToken, loadProfile]);

    async function onSaveProfile(e) {
        e.preventDefault();
        setBusy(true);
        setNotice(null);

        const result = await saveMyProfile(accessToken, form);
        setBusy(false);

        if (!result.ok) {
            setNotice({ kind: 'err', text: result.message ?? `저장 실패 (${result.status})` });
            return;
        }
        setProfile(result.data);
        setProfileMissing(false);
        setNotice({
            kind: 'ok',
            text: 'customer-service 에 저장했습니다. 저장 키는 요청이 아니라 토큰의 sub 입니다 — '
                + '경로나 본문으로 받으면 남의 식별자를 적어 넣는 것으로 남의 프로필이 열립니다.',
        });
    }

    async function onLoadPermissions() {
        const result = await myPermissions(accessToken);
        if (!result.ok) {
            setNotice({ kind: 'err', text: `권한 조회 실패 (${result.status})` });
            return;
        }
        setPermissions(result.data);
    }

    async function onLoadJwks() {
        const result = await jwks();
        if (!result.ok) {
            setNotice({ kind: 'err', text: `JWKS 조회 실패 (${result.status})` });
            return;
        }
        setKeys(result.data.keys);
    }

    async function onRefresh() {
        setBusy(true);
        const result = await refresh();
        setBusy(false);
        setNotice(result.ok
            ? { kind: 'ok', text: '새 토큰을 받았습니다. 권한과 리비전이 그 시점 값으로 다시 실렸습니다. 위 만료 시각이 바뀐 걸 보세요.' }
            : { kind: 'err', text: result.message ?? `재발급 실패 (${result.status})` });
    }

    const myKid = tokens?.accessToken ? JSON.parse(atob(tokens.accessToken.split('.')[0].replace(/-/g, '+').replace(/_/g, '/'))).kid : null;

    return (
        <div className="page">
            <h1>마이페이지</h1>
            <p className="lead">
                로그인 뒤에 볼 수 있는 것들. <b>프로필은 customer-service</b>, <b>권한은 auth-service</b> 에서
                옵니다 — 같은 토큰 하나로 두 서버를 부릅니다.
            </p>

            <Notice kind={notice?.kind}>{notice?.text}</Notice>

            <div className="row sticky-actions">
                <button onClick={onRefresh} disabled={busy}>토큰 재발급</button>
                <button onClick={onLoadPermissions}>권한 조회 (auth)</button>
                <button onClick={onLoadJwks}>JWKS 보기</button>
                <button onClick={logout}>로그아웃</button>
            </div>

            {/* ── 프로필: customer-service ───────────────────────────── */}
            <div className="card">
                <div className="card-head">
                    <h2>프로필</h2>
                    <span className="pill">customer-service</span>
                </div>

                {profileMissing && (
                    <p className="field-hint">
                        아직 프로필이 없습니다(<code>404</code>). 가입은 auth-service 에서만 일어났고,
                        customer-service 는 아무것도 모릅니다 — 서비스마다 자기 DB 를 갖기 때문입니다.
                        아래에서 만들어 보세요.
                        <br /><br />
                        원래 설계는 auth 가 <code>SubjectRegisteredEvent</code> 를 발행하고 customer 가
                        받아서 만드는 것인데, 서비스 간 이벤트 전달이 아직 없습니다.
                    </p>
                )}

                <form onSubmit={onSaveProfile}>
                    <label htmlFor="p-name">이름</label>
                    <input id="p-name" required value={form.name}
                           onChange={(e) => setForm({ ...form, name: e.target.value })} />

                    <label htmlFor="p-phone">휴대폰</label>
                    <input id="p-phone" value={form.phoneNumber}
                           onChange={(e) => setForm({ ...form, phoneNumber: e.target.value })} />

                    <label htmlFor="p-email">이메일 (표시용)</label>
                    <input id="p-email" type="email" value={form.email}
                           onChange={(e) => setForm({ ...form, email: e.target.value })} />
                    <p className="field-hint">
                        여기서 고쳐도 <b>로그인 이메일은 바뀌지 않습니다.</b> 로그인 식별자로서의 이메일은
                        auth-service 가 소유합니다. 두 값이 갈라질 수 있다는 것이 서비스를 나눈 대가입니다.
                    </p>

                    <div className="row">
                        <button className="primary" type="submit" disabled={busy}>
                            {profileMissing ? '프로필 만들기' : '저장'}
                        </button>
                    </div>
                </form>

                {profile && (
                    <div className="kv">
                        <span>customerId</span><code>{profile.customerId}</code>
                        <span>생성</span><span>{new Date(profile.createdAt).toLocaleString()}</span>
                        <span>수정</span><span>{new Date(profile.updatedAt).toLocaleString()}</span>
                    </div>
                )}

                {profile && claims && (
                    <p className={`field-hint ${profile.customerId === claims.sub ? 'ok-text' : 'warn'}`}>
                        {profile.customerId === claims.sub
                            ? '↑ customerId 가 토큰의 sub 와 같습니다. customer-service 가 따로 번호를 매기지 않고 auth 가 채번한 값을 그대로 씁니다.'
                            : '↑ customerId 와 토큰의 sub 가 다릅니다. 뭔가 잘못됐습니다.'}
                    </p>
                )}
            </div>

            {/* ── 토큰 ───────────────────────────────────────────────── */}
            <TokenInspector label="access 토큰" token={tokens?.accessToken} kind="access" />
            <TokenInspector label="refresh 토큰" token={tokens?.refreshToken} kind="refresh" />

            {/* ── 권한: auth-service ─────────────────────────────────── */}
            {permissions && (
                <div className="card">
                    <div className="card-head">
                        <h2>권한 조회 결과</h2>
                        <span className="pill">auth-service</span>
                    </div>
                    <div className="kv">
                        <span>realm</span><code>{permissions.realm}</code>
                        <span>permissions</span>
                        <span>
                            {permissions.permissions.length === 0
                                ? <em className="hint">없음</em>
                                : permissions.permissions.map((p) => <code key={p} className="chip">{p}</code>)}
                        </span>
                    </div>
                    <p className="field-hint">
                        토큰의 <code>authLs</code> 와 같은 값입니다. 이 API 는 토큰을 읽어 돌려줄 뿐이라
                        <b> 새로 조회하는 게 아닙니다</b> — 방금 권한이 바뀌었어도 여기엔 안 나옵니다.
                        반영하려면 재발급을 받아야 합니다.
                    </p>
                </div>
            )}

            {/* ── JWKS ───────────────────────────────────────────────── */}
            {keys && (
                <div className="card">
                    <div className="card-head">
                        <h2>JWKS — 이 realm 의 공개키</h2>
                        <span className="pill">auth-service</span>
                    </div>
                    <table className="claims">
                        <tbody>
                        {keys.map((key) => (
                            <tr key={key.kid}>
                                <td className="claim-key">
                                    <code>{key.kid}</code>
                                    {key.kid === myKid && <span className="claim-title ok-text">내 토큰의 키</span>}
                                </td>
                                <td className="claim-value">
                                    <code>{key.kty} / {key.alg} / use={key.use}</code>
                                    <p className="claim-note">n={key.n.slice(0, 40)}…</p>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                    <p className="field-hint">
                        <b>포털 키 하나만 나옵니다.</b> JWKS 는 realm 마다 주소가 다르고,
                        customer-service 는 이 주소만 설정해 두었습니다. 그래서 그 서비스는
                        <b>어드민 토큰을 검증할 수조차 없습니다</b> — <code>kid</code> 에 해당하는
                        공개키가 없어 서명 검증에서 죽습니다. realm 경계가 코드가 아니라
                        <b>설정</b>에 있는 셈입니다.
                    </p>
                </div>
            )}
        </div>
    );
}
