import { useEffect, useState } from 'react';
import { onRequest } from '../api/http';

/**
 * 오간 요청을 쌓아서 보여준다.
 *
 * 무슨 요청이 나갔는지가 이 데모의 절반이다. 특히 눈여겨볼 것:
 * <ul>
 *   <li>로그인이 <b>두 번</b>의 요청이다 — 코드 발송과 코드 검증. 토큰은 두 번째에만 나온다.</li>
 *   <li>realm 이 언제나 경로 안에 있다 — <code>/realms/admin/...</code>.
 *       토큰이 스스로 realm 을 주장하지 않는다.</li>
 *   <li>격리 실험에서 <code>/realms/portal/...</code> 로 나가는 요청의 상태코드.</li>
 * </ul>
 */
export default function RequestLog() {
    const [entries, setEntries] = useState([]);
    const [open, setOpen] = useState(true);

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
                            <span className="server auth">auth</span>
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

function statusClass(status) {
    if (status === 0) return 'blocked';
    if (status >= 500) return 's5';
    if (status >= 400) return 's4';
    return 's2';
}
