import { useEffect, useState } from 'react';
import { CLAIM_NOTES, decode, formatDuration, formatEpoch, secondsUntil } from '../api/jwt';

/**
 * 토큰을 뜯어서 보여준다.
 *
 * 이 화면이 성립한다는 것 자체가 알아둘 점이다 — payload 는 <b>암호화가 아니라 base64url</b> 이라
 * 누구나 읽는다. 서명은 "내용이 바뀌지 않았음" 을 보장할 뿐 "내용이 비밀임" 을 보장하지 않는다.
 * 그래서 토큰에 담은 값은 공개된 것과 같다.
 */
export default function TokenInspector({ label, token, kind }) {
    const decoded = decode(token);
    const [now, setNow] = useState(Date.now());
    const [showRaw, setShowRaw] = useState(false);

    // 만료까지 남은 시간을 1초마다 갱신한다. 토큰이 살아 있는 물건이라는 감각이 생긴다.
    useEffect(() => {
        const timer = setInterval(() => setNow(Date.now()), 1000);
        return () => clearInterval(timer);
    }, []);

    if (!decoded) {
        return (
            <div className="card">
                <h2>{label}</h2>
                <p className="hint">읽을 수 없는 토큰입니다.</p>
            </div>
        );
    }

    const { header, payload, signature } = decoded;
    const remaining = secondsUntil(payload.exp);
    const expired = remaining !== null && remaining <= 0;

    return (
        <div className="card">
            <div className="card-head">
                <h2>{label}</h2>
                <span className={`pill ${expired ? 'danger' : 'ok'}`}>
                    {expired ? '만료됨' : `${formatDuration(remaining)} 남음`}
                </span>
            </div>

            <div className="kv">
                <span>서명 알고리즘</span><code>{header.alg}</code>
                <span>키 식별자 (kid)</span><code>{header.kid ?? '(없음)'}</code>
                <span>발급</span><span>{formatEpoch(payload.iat)}</span>
                <span>만료</span><span>{formatEpoch(payload.exp)}</span>
            </div>

            <p className="field-hint">
                <b>kid</b> 가 헤더에 있는 이유: 검증하는 쪽은 토큰을 열어보기 전에 어느 공개키를 쓸지
                정해야 합니다. 내용을 믿으려면 먼저 서명을 확인해야 하니까요. 그래서 키 식별자는
                서명 대상 밖인 헤더에 있습니다.
            </p>

            <h3>클레임</h3>
            <table className="claims">
                <tbody>
                {Object.entries(payload).map(([key, value]) => {
                    const [title, note] = CLAIM_NOTES[key] ?? [key, ''];
                    return (
                        <tr key={key}>
                            <td className="claim-key">
                                <code>{key}</code>
                                <span className="claim-title">{title}</span>
                            </td>
                            <td className="claim-value">
                                <code>{renderValue(key, value)}</code>
                                {note && <p className="claim-note">{note}</p>}
                            </td>
                        </tr>
                    );
                })}
                </tbody>
            </table>

            <div className="row">
                <button onClick={() => setShowRaw(!showRaw)}>
                    {showRaw ? '원문 숨기기' : '원문 보기'}
                </button>
                <button onClick={() => navigator.clipboard?.writeText(token)}>복사</button>
            </div>

            {showRaw && (
                <>
                    <pre className="raw">{token}</pre>
                    <p className="field-hint">
                        점(<code>.</code>)으로 세 조각입니다 — 헤더 · 페이로드 · 서명.
                        앞의 둘은 base64url 이라 그대로 읽힙니다.
                        서명({signature.slice(0, 16)}…)만 개인키가 있어야 만들 수 있습니다.
                        <br />
                        <code>echo &lt;토큰&gt; | cut -d. -f2 | base64 -d</code> 로 터미널에서도 같은 걸 볼 수 있습니다.
                    </p>
                </>
            )}

            {kind === 'refresh' && (
                <p className="field-hint warn">
                    이 토큰이 가야 할 곳은 <code>/token/refresh</code> <b>한 곳뿐</b>입니다.
                    수명이 access 토큰보다 훨씬 길어서, 새면 그만큼 오래 새 토큰을 찍어낼 수 있습니다.
                </p>
            )}
        </div>
    );
}

function renderValue(key, value) {
    if (key === 'exp' || key === 'iat') return `${value} (${formatEpoch(value)})`;
    if (Array.isArray(value)) return value.join(', ');
    if (value === '') return '(빈 값)';
    return String(value);
}
