import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { jwks, userinfo } from '../api/auth';
import { myProfile, saveMyProfile } from '../api/customer';
import TokenInspector from '../components/TokenInspector';
import DevPanel from '../components/DevPanel';
import Notice from '../components/Notice';

/**
 * 마이페이지.
 *
 * 위쪽은 사용자가 쓰는 화면이고, 아래 접힌 패널은 이 저장소가 설명하려는 것이다.
 *
 * 요점은 두 서버를 부른다는 것이다. 프로필은 customer-service 에서 오고 권한은 auth-service
 * 에서 온다. 같은 토큰 하나로 둘 다 부르고, customer-service 를 부를 때 auth 는 등장하지
 * 않는다 - 공개키로 직접 서명을 검증하기 때문이다.
 */
export default function MyPage() {
    const { tokens, claims, refresh } = useAuth();

    const [profile, setProfile] = useState(null);
    const [profileMissing, setProfileMissing] = useState(false);
    const [form, setForm] = useState({ name: '', phoneNumber: '', email: '' });
    const [owner, setOwner] = useState(null);
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
        setNotice({ kind: 'ok', text: '저장했습니다.' });
    }

    async function onLoadUserInfo() {
        const result = await userinfo(accessToken);
        if (!result.ok) {
            setNotice({ kind: 'err', text: `userinfo 조회 실패 (${result.status})` });
            return;
        }
        setOwner(result.data);
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
            ? { kind: 'ok', text: '새 토큰을 받았습니다. 아래 만료 시각이 바뀐 것을 보세요.' }
            : { kind: 'err', text: result.message ?? `재발급 실패 (${result.status})` });
    }

    const myKid = accessToken
        ? JSON.parse(atob(accessToken.split('.')[0].replace(/-/g, '+').replace(/_/g, '/'))).kid
        : null;

    return (
        <div className="page">
            <header className="page-head">
                <h1>마이페이지</h1>
                <p className="lead">
                    {profile?.name ? `${profile.name}님, 안녕하세요.` : '프로필을 완성해 주세요.'}
                </p>
            </header>

            <Notice kind={notice?.kind}>{notice?.text}</Notice>

            <div className="card">
                <h2>프로필</h2>

                {profileMissing && (
                    <p className="field-hint">아직 프로필이 없습니다. 아래에서 만들어 주세요.</p>
                )}

                <form onSubmit={onSaveProfile}>
                    <label htmlFor="p-name">이름</label>
                    <input id="p-name" required value={form.name}
                           onChange={(e) => setForm({ ...form, name: e.target.value })} />

                    <label htmlFor="p-phone">휴대폰</label>
                    <input id="p-phone" placeholder="010-0000-0000" value={form.phoneNumber}
                           onChange={(e) => setForm({ ...form, phoneNumber: e.target.value })} />

                    <label htmlFor="p-email">이메일</label>
                    <input id="p-email" type="email" value={form.email}
                           onChange={(e) => setForm({ ...form, email: e.target.value })} />

                    <div className="row">
                        <button className="btn btn-primary" type="submit" disabled={busy}>
                            {profileMissing ? '프로필 만들기' : '저장'}
                        </button>
                    </div>
                </form>
            </div>

            <DevPanel title="이 화면이 실제로 하는 일">
                <h3>두 서버를 부른다</h3>
                <p className="field-hint">
                    프로필은 <b>customer-service</b>, 권한은 <b>auth-service</b> 에서 옵니다.
                    같은 토큰 하나로 둘 다 부르고, customer-service 를 부를 때 auth 는 등장하지
                    않습니다. 아래 요청 로그에서 확인해 보세요. customer-service 가 공개키로 직접
                    서명을 검증하기 때문입니다.
                </p>

                {profileMissing && (
                    <p className="field-hint">
                        프로필이 <code>404</code> 인 것은 가입이 auth-service 에서만 일어났고
                        customer-service 는 아무것도 모르기 때문입니다. 서비스마다 자기 DB 를
                        갖습니다. 원래 설계는 auth 가 <code>SubjectRegisteredEvent</code> 를 발행하고
                        customer 가 받아 만드는 것인데, 서비스 간 이벤트 전달이 아직 없습니다.
                    </p>
                )}

                {profile && claims && (
                    <>
                        <div className="kv">
                            <span>customerId</span><code>{profile.customerId}</code>
                            <span>id_token sub</span><code>{claims.sub}</code>
                        </div>
                        <p className={`field-hint ${profile.customerId === claims.sub ? 'ok-text' : 'warn'}`}>
                            {profile.customerId === claims.sub
                                ? '두 값이 같습니다. customer-service 가 따로 번호를 매기지 않고 auth 가 채번한 값을 그대로 씁니다. 저장 키도 요청이 아니라 토큰의 sub 입니다 - 경로나 본문으로 받으면 남의 식별자를 적어 넣는 것으로 남의 프로필이 열립니다.'
                                : '두 값이 다릅니다. 뭔가 잘못됐습니다.'}
                        </p>
                        <p className="field-hint">
                            위에서 이메일을 고쳐도 <b>로그인 이메일은 바뀌지 않습니다.</b> 로그인
                            식별자로서의 이메일은 auth-service 가 소유합니다. 두 값이 갈라질 수 있다는
                            것이 서비스를 나눈 대가입니다.
                        </p>
                    </>
                )}

                <h3>토큰</h3>
                <div className="row">
                    <button onClick={onRefresh} disabled={busy}>토큰 재발급</button>
                    <button onClick={onLoadUserInfo}>userinfo (auth)</button>
                    <button onClick={onLoadJwks}>JWKS 보기</button>
                </div>

                <TokenInspector label="id_token (누가 로그인했나. 이 앱이 읽는 것)" token={tokens?.idToken} kind="id" />

                <TokenInspector label="access 토큰 (API 에 보내는 것)" token={tokens?.accessToken} kind="access" />

                <TokenInspector label="refresh 토큰" token={tokens?.refreshToken} kind="refresh" />

                {owner && (
                    <>
                        <h3>userinfo 결과</h3>
                        <div className="kv">
                            <span>sub</span><code>{owner.sub}</code>
                            <span>name</span><span>{owner.name ?? <em className="hint">없음</em>}</span>
                            <span>email</span><span>{owner.email ?? <em className="hint">없음</em>}</span>
                            <span>phone_number</span><span>{owner.phone_number ?? <em className="hint">없음</em>}</span>
                        </div>
                        <p className="field-hint">
                            auth 가 아는 신원 정보입니다(OIDC userinfo). 위의 프로필은 customer-service 가 가진 업무
                            정보라 주인이 다릅니다. 권한은 여기 없습니다. 무엇을 할 수 있는지는 각 서비스가 판단합니다.
                        </p>
                    </>
                )}

                {keys && (
                    <>
                        <h3>JWKS 이 realm 의 공개키</h3>
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
                            <b>어드민 토큰을 검증할 수조차 없습니다</b> - <code>kid</code> 에 해당하는
                            공개키가 없어 서명 검증에서 죽습니다. realm 경계가 코드가 아니라
                            <b>설정</b>에 있는 셈입니다.
                        </p>
                    </>
                )}
            </DevPanel>
        </div>
    );
}
