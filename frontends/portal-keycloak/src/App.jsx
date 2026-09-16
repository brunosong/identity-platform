import { useCallback, useEffect, useState } from 'react';
import * as oidc from './oidc';
import { CLAIM_NOTES, decode, formatDuration, secondsUntil } from './jwt';

/**
 * StrictMode 는 개발 중에 마운트 효과를 두 번 실행한다. 토큰 교환이 두 번 나가면 두 번째는
 * 이미 써버린 code 라 실패한다. 컴포넌트 밖에 두어야 두 번째 실행에서도 같은 값이 보인다.
 */
let exchanging = false;

export default function App() {
    const [tokens, setTokens] = useState(null);
    const [error, setError] = useState(null);
    const [entries, setEntries] = useState(() => oidc.readLog());
    const [, tick] = useState(0);

    // 돌아온 직후인지 확인한다. 주소창에 code 가 실려 있으면 그렇다.
    useEffect(() => {
        const callback = oidc.readCallback();
        if (!callback) return;

        oidc.clearCallbackFromUrl();

        if (callback.error) {
            setError(`${callback.error}: ${callback.description ?? ''}`);
            return;
        }
        if (exchanging) return;
        exchanging = true;

        oidc.completeLogin(callback)
            .then(setTokens)
            .catch((cause) => setError(cause.message))
            .finally(() => {
                exchanging = false;
                setEntries(oidc.readLog());
            });
    }, []);

    // 만료까지 남은 시간을 1초마다 다시 그린다.
    useEffect(() => {
        const timer = setInterval(() => tick((n) => n + 1), 1000);
        return () => clearInterval(timer);
    }, []);

    const login = useCallback(() => {
        setError(null);
        oidc.beginLogin().catch((cause) => {
            setError(cause.message);
            setEntries(oidc.readLog());
        });
    }, []);

    const renew = useCallback(async () => {
        setError(null);
        try {
            setTokens(await oidc.refresh(tokens.refresh_token));
        } catch (cause) {
            setError(cause.message);
        }
        setEntries(oidc.readLog());
    }, [tokens]);

    const logout = useCallback(async () => {
        setError(null);
        try {
            const url = await oidc.logoutUrl(tokens.id_token);
            oidc.clearLog();
            location.assign(url);
        } catch (cause) {
            setError(cause.message);
        }
    }, [tokens]);

    return (
        <div className="shell">
            <header>
                <h1>포털 (Keycloak)</h1>
                <p className="muted">
                    Authorization Code + PKCE 로 로그인한다. 비밀번호는 이 앱을 거치지 않는다.
                </p>
                <dl className="config">
                    <dt>issuer</dt>
                    <dd><code>{oidc.config.baseUrl}/realms/{oidc.config.realm}</code></dd>
                    <dt>client_id</dt>
                    <dd><code>{oidc.config.clientId}</code></dd>
                    <dt>redirect_uri</dt>
                    <dd><code>{oidc.REDIRECT_URI}</code></dd>
                </dl>
            </header>

            {error && <p className="error">{error}</p>}

            {!tokens ? (
                <section className="panel">
                    <h2>아직 로그인하지 않았다</h2>
                    <p>
                        아래를 누르면 Keycloak 로그인 화면으로 넘어간다. 아이디와 비밀번호를 받는 것은
                        이 앱이 아니라 Keycloak 이다. 그래서 이 앱의 코드에는 비밀번호를 다루는 자리가
                        아예 없다. 그것이 이 흐름을 쓰는 이유다.
                    </p>
                    <button onClick={login}>Keycloak 으로 로그인</button>
                </section>
            ) : (
                <>
                    <section className="panel">
                        <h2>토큰을 받았다</h2>
                        <div className="actions">
                            <button onClick={renew}>재발급</button>
                            <button onClick={logout}>로그아웃</button>
                        </div>
                        <p className="muted">
                            로그아웃은 토큰만 버리는 것으로 끝나지 않는다. Keycloak 에 남은 SSO 세션까지
                            끊어야 다음 로그인에서 비밀번호를 다시 묻는다.
                        </p>
                    </section>

                    <TokenPanel title="access_token" token={tokens.access_token} />
                    <TokenPanel title="id_token" token={tokens.id_token} />
                    <TokenPanel title="refresh_token" token={tokens.refresh_token} />
                </>
            )}

            <section className="panel">
                <h2>흐름 기록</h2>
                {entries.length === 0 ? (
                    <p className="muted">아직 없다.</p>
                ) : (
                    <ol className="log">
                        {entries.map((entry, index) => (
                            <li key={index}>
                                <span className="muted">{entry.at}</span> {entry.text}
                            </li>
                        ))}
                    </ol>
                )}
            </section>
        </div>
    );
}

function TokenPanel({ title, token }) {
    const parsed = decode(token);
    if (!parsed) return null;

    const remaining = secondsUntil(parsed.payload.exp);

    return (
        <section className="panel">
            <h2>
                {title}{' '}
                <span className={remaining > 0 ? 'ok' : 'error'}>
                    {formatDuration(remaining)} 남음
                </span>
            </h2>
            <p className="muted">
                서명 알고리즘 <code>{parsed.header.alg}</code>, 키 <code>{parsed.header.kid}</code>
            </p>
            <table>
                <tbody>
                {Object.entries(parsed.payload).map(([key, value]) => (
                    <tr key={key}>
                        <th>{key}</th>
                        <td>
                            <code>{typeof value === 'object' ? JSON.stringify(value) : String(value)}</code>
                            {CLAIM_NOTES[key] && <p className="muted">{CLAIM_NOTES[key]}</p>}
                        </td>
                    </tr>
                ))}
                </tbody>
            </table>
        </section>
    );
}
