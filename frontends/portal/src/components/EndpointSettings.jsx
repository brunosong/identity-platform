import { useState } from 'react';
import { defaultEndpoints, endpoints, resetEndpoints, saveEndpoints } from '../api/config';

/**
 * 어느 서버를 부르고 있는지 늘 보이게 하고, 바꿀 수 있게 한다.
 *
 * 서버가 둘로 보이는 것 자체가 이 데모의 요점이다 — 게이트웨이가 없어서 프론트가 두 곳을 직접
 * 부르고, 그래서 두 서버가 각각 CORS 를 열어줘야 한다.
 */
export default function EndpointSettings() {
    const [open, setOpen] = useState(false);
    const [values, setValues] = useState(endpoints);

    function apply() {
        saveEndpoints(values);
        location.reload();   // 주소가 바뀌면 로그인 상태도 의미가 없다
    }

    function restore() {
        resetEndpoints();
        location.reload();
    }

    const current = endpoints();

    return (
        <div className="endpoints">
            <button className="endpoints-toggle" onClick={() => setOpen(!open)}>
                <span className="dot" /> auth {short(current.auth)} · customer {short(current.customer)}
            </button>

            {open && (
                <div className="endpoints-panel">
                    <p className="hint">
                        포트를 바꿔 띄웠다면 여기서 맞추세요. 이 브라우저에만 저장됩니다.
                        <br />
                        서버 쪽 <code>app.cors.allowed-origins</code> 에 <code>{location.origin}</code> 이
                        있어야 요청이 나갑니다.
                    </p>

                    <label>auth-service</label>
                    <input
                        value={values.auth}
                        onChange={(e) => setValues({ ...values, auth: e.target.value })}
                    />

                    <label>customer-service</label>
                    <input
                        value={values.customer}
                        onChange={(e) => setValues({ ...values, customer: e.target.value })}
                    />

                    <div className="row">
                        <button className="primary" onClick={apply}>적용 (새로고침)</button>
                        <button onClick={restore}>기본값으로</button>
                    </div>

                    <p className="hint">
                        기본값: {defaultEndpoints().auth} · {defaultEndpoints().customer}
                        <br />(<code>.env</code> 에서 옵니다)
                    </p>
                </div>
            )}
        </div>
    );
}

function short(url) {
    return url.replace(/^https?:\/\//, '');
}
