import { useEffect, useState } from 'react';
import { onRequest } from '../api/http';

/**
 * 오간 요청을 쌓아서 보여준다.
 *
 * 화면이 어느 서버를 언제 부르는지 보이는 것이 이 앱의 절반이다. 특히 눈여겨볼 것:
 * 로그인은 auth 로, 프로필은 customer 로 간다. 그리고 <b>customer 를 부를 때 auth 는 등장하지 않는다</b> —
 * 토큰 검증을 위해 auth 에 물어보지 않기 때문이다(공개키로 직접 검증한다).
 */
export default function RequestLog() {
    const [entries, setEntries] = useState([]);
    // 기본은 접힌 상태다. 펼쳐 두면 화면 아래 3분의 1을 늘 차지해서, 서비스가 아니라
    // 개발 도구처럼 보인다. 볼 사람은 한 번 누르면 된다.
    const [open, setOpen] = useState(false);

    useEffect(() => onRequest((entry) => {
        setEntries((prev) => [entry, ...prev].slice(0, 50));
    }), []);

    return (
        <aside className={`log ${open ? '' : 'collapsed'}`}>
            <button className="log-header" onClick={() => setOpen(!open)}>
                주고받은 요청 <span className="count">{entries.length}</span>
                <span className="chevron">{open ? '▾' : '▴'}</span>
            </button>

            {open && (
                <div className="log-body">
                    {entries.length === 0 && <p className="hint">아직 없습니다.</p>}
                    {entries.map((entry, index) => (
                        <div className="log-line" key={index}>
                            <span className="time">{entry.at.toLocaleTimeString()}</span>
                            <span className={`server ${serverOf(entry.baseUrl)}`}>{serverOf(entry.baseUrl)}</span>
                            <span className="method">{entry.method}</span>
                            <span className="path">{entry.path}</span>
                            <span className={`status ${statusClass(entry.status)}`}>
                                {entry.status === 0 ? 'blocked' : entry.status}
                            </span>
                        </div>
                    ))}
                </div>
            )}
        </aside>
    );
}

function serverOf(baseUrl) {
    return baseUrl?.includes('8081') || baseUrl?.includes('customer') ? 'customer' : 'auth';
}

function statusClass(status) {
    if (status === 0) return 'blocked';
    if (status >= 500) return 's5';
    if (status >= 400) return 's4';
    return 's2';
}
