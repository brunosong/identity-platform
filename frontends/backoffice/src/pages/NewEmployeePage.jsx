import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { registerEmployee, roles as fetchRoles } from '../api/admin';
import Notice from '../components/Notice';

/**
 * 직원 등록 — <b>관리자가 남의 계정을 만든다.</b>
 *
 * <p>직원 realm 은 셀프 가입이 닫혀 있어서 직원 계정이 생기는 길은 이 화면 하나다. 역할도 여기서 함께 준다.
 *
 * <p>인증번호가 없다. 관리자가 만드는 것이라 그 주소의 주인임을 지금 증명할 사람이 없다. 그래서
 * 이 경로로 만든 계정의 이메일은 <i>확인되지 않은</i> 주소이고, 그 직원이 처음 인증번호로 로그인할 때
 * 확인됨으로 올라간다.
 *
 * <p>비밀번호 칸은 없다. 직원 계정은 이메일 계정만 갖기 때문이다 — "초기 비밀번호를 정해
 * 전달한다" 는 단계가 통째로 사라진다. 그 단계는 실무에서 비밀번호가 메신저와 메일을 떠돌게
 * 만드는 자리이기도 하다.
 */
export default function NewEmployeePage() {
    const navigate = useNavigate();
    const { tokens } = useAuth();
    const accessToken = tokens?.accessToken;

    const [form, setForm] = useState(() => ({
        employeeId: `E${Date.now().toString().slice(-6)}`,
        name: '',
        email: `kim-${Date.now()}@example.com`,
        mobile: '',
    }));
    const [availableRoles, setAvailableRoles] = useState([]);
    const [roleIds, setRoleIds] = useState([]);
    const [created, setCreated] = useState(null);
    const [notice, setNotice] = useState(null);
    const [busy, setBusy] = useState(false);

    const update = (key) => (e) => setForm({ ...form, [key]: e.target.value });

    /**
     * 배정할 수 있는 역할을 미리 받아둔다.
     *
     * 역할을 함께 주지 않으면 계정은 생기지만 권한이 없어서, 로그인은 되는데 아무 화면도 열리지
     * 않는다. 그 상태를 일부러 만들어 보는 것도 실험이다 — 체크를 하나도 하지 않고 만든 뒤
     * 다른 브라우저 창에서 그 이메일로 로그인해 보면 된다.
     */
    const loadRoles = useCallback(async () => {
        const result = await fetchRoles(accessToken);
        if (result.ok) setAvailableRoles(result.data ?? []);
    }, [accessToken]);

    useEffect(() => { loadRoles(); }, [loadRoles]);

    function toggleRole(roleId) {
        setRoleIds((prev) => (
            prev.includes(roleId) ? prev.filter((id) => id !== roleId) : [...prev, roleId]
        ));
    }

    async function onSubmit(e) {
        e.preventDefault();
        setBusy(true);
        setNotice(null);
        setCreated(null);

        const result = await registerEmployee(accessToken, {
            employeeId: form.employeeId.trim(),
            name: form.name.trim(),
            email: form.email.trim(),
            mobile: form.mobile.trim() || null,
            roleIds,
        });
        setBusy(false);

        if (!result.ok) {
            setNotice({ kind: 'err', text: result.message ?? `가입 실패 (${result.status})` });
            return;
        }
        setCreated({ ...form, essentialId: result.data.essentialId, roleIds });
        setNotice({
            kind: 'ok',
            text: `계정이 만들어졌습니다. 이 사람은 지금 바로 ${form.email} 로 인증번호를 받아 `
                + '로그인할 수 있습니다 — 전달할 비밀번호가 없습니다.',
        });
    }

    return (
        <div className="page narrow">
            <h1>직원 등록</h1>
            <p className="lead">
                관리자가 남의 계정을 만듭니다. 본인이 직접 만드는 것은 <b>직원 가입</b> 화면입니다.
            </p>

            <Notice kind={notice?.kind}>{notice?.text}</Notice>

            {created && (
                <div className="card">
                    <div className="card-head">
                        <h2>방금 만든 계정</h2>
                        <span className="pill ok">생성됨</span>
                    </div>
                    <div className="kv">
                        <span>subjectId (esntlId)</span><code>{created.essentialId}</code>
                        <span>사번</span><code>{created.employeeId}</code>
                        <span>이름</span><span>{created.name}</span>
                        <span>이메일</span><code>{created.email}</code>
                        <span>초기 역할</span>
                        <span>
                            {created.roleIds.length === 0
                                ? <em className="hint">없음 — 로그인은 되지만 아무 화면도 열리지 않습니다</em>
                                : availableRoles
                                    .filter((r) => created.roleIds.includes(r.roleId))
                                    .map((r) => <span className="chip" key={r.roleId}>{r.roleCode}</span>)}
                        </span>
                    </div>
                    <p className="field-hint">
                        <code>esntlId</code> 는 auth 가 채번한 직원 식별자입니다. 고객의 UUID 와 다른
                        체계라서, subjectId 만으로는 유일하지 않고 <code>(realm, subject_id)</code> 로
                        유일합니다.
                    </p>
                    <div className="row">
                        <button onClick={() => navigate('/login', { state: { justRegistered: created.email } })}>
                            이 계정으로 로그인해 보기 (지금 세션은 끊깁니다)
                        </button>
                    </div>
                    <p className="field-hint">
                        <b>다른 브라우저 창에서</b> 열어보는 편이 낫습니다. 권한 없는 직원에게 이 앱이
                        어떻게 보이는지를 지금 세션을 유지한 채 나란히 볼 수 있습니다.
                    </p>
                </div>
            )}

            <form className="card" onSubmit={onSubmit}>
                <label htmlFor="employeeId">사번</label>
                <input id="employeeId" required value={form.employeeId} onChange={update('employeeId')} />

                <label htmlFor="name">이름</label>
                <input id="name" required placeholder="김직원" value={form.name} onChange={update('name')} />

                <label htmlFor="email">이메일 (로그인 식별자)</label>
                <input id="email" type="email" required value={form.email} onChange={update('email')} />
                <p className="field-hint">
                    이 주소로 인증번호가 갑니다. 같은 주소로 고객 계정이 이미 있어도 상관없습니다 —
                    이메일은 <code>(realm, email)</code> 로 유일해서, 직원 서랍과 고객 서랍은
                    서로 다른 신원입니다.
                </p>

                <label htmlFor="mobile">휴대폰 (선택)</label>
                <input id="mobile" value={form.mobile} onChange={update('mobile')} />

                <label>초기 역할</label>
                {availableRoles.length === 0 ? (
                    <p className="field-hint">역할 목록을 불러오지 못했습니다. 역할 없이도 만들 수 있습니다.</p>
                ) : (
                    <div className="checks">
                        {availableRoles.map((role) => (
                            <label className="check" key={role.roleId}>
                                <input
                                    type="checkbox"
                                    checked={roleIds.includes(role.roleId)}
                                    onChange={() => toggleRole(role.roleId)}
                                />
                                <span>
                                    <code>{role.roleCode}</code> {role.roleName}
                                    {role.description && <span className="check-desc">{role.description}</span>}
                                </span>
                            </label>
                        ))}
                    </div>
                )}
                <p className="field-hint">
                    비워 두면 계정은 생기지만 권한이 없습니다 — 로그인은 되는데 아무 관리 화면도
                    열리지 않습니다. 권한은 신원(Principal)이 아니라 인가 쪽
                    (<code>authz_subject_role</code>)이 소유하고, 계정 생성과 역할 배정은 별개의 호출입니다.
                </p>

                <div className="row">
                    <button className="primary" type="submit" disabled={busy}>
                        {busy ? '처리 중…' : '계정 만들기'}
                    </button>
                </div>
            </form>

            <div className="card muted-card">
                <h2>여기서 무슨 일이 일어나나</h2>
                <ol className="steps">
                    <li>
                        <code>POST /api/admin/realms/admin/users</code> — 경로의 realm 은
                        <b> 대상</b>이고, 호출자의 realm 은 토큰이 정합니다(언제나 어드민). 어드민이
                        포털 계정을 만드는 것은 정상이라 둘을 한 값으로 묶으면 안 됩니다.
                    </li>
                    <li>
                        auth 가 <code>esntlId</code> 를 채번하고 Principal 과 <b>이메일 계정</b>을
                        만듭니다. 비밀번호 계정은 만들지 않습니다.
                    </li>
                    <li>고른 역할이 있으면 <code>authz_subject_role</code> 에 배정됩니다.</li>
                    <li><b>토큰은 나오지 않습니다.</b> 만든 사람과 쓸 사람이 다르니 당연합니다.</li>
                </ol>
            </div>

            <div className="card muted-card">
                <h2>셀프 가입과 무엇이 다른가</h2>
                <p className="hint">
                    본인이 <b>직원 가입</b>으로 만들면 인증번호를 받아내야 하므로 그 주소의 주인임이
                    증명됩니다. 여기서 만들면 그 증명이 없습니다 — 관리자가 오타를 내면 엉뚱한
                    주소가 로그인 식별자가 되고, 그 주소의 주인이 인증번호를 받아 들어옵니다.
                    <br /><br />
                    대신 여기서만 <b>역할을 줄 수 있습니다.</b> 셀프 가입은 권한 없는 껍데기만
                    만듭니다 — 신원을 만드는 일과 권한을 주는 일이 갈려 있다는 것이 어드민 셀프
                    가입을 열어도 되는 이유입니다.
                </p>
            </div>
        </div>
    );
}
