import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { permissions as fetchPermissions, revision as fetchRevision, roles as fetchRoles } from '../api/admin';
import Notice from '../components/Notice';

/**
 * 인가 정책 — 어드민 realm 의 역할·권한·리비전을 들여다본다.
 *
 * <b>조회만 한다.</b> 편집 API 는 서버에 있지만(RoleAdminApiController 등) 이 화면에는 두지
 * 않았다. 이 앱의 목적은 "인가가 어떻게 생겼는지" 를 보는 것이지 정책을 운영하는 것이 아니다.
 *
 * <p>세 호출 모두 <code>AUTHZ_MANAGE</code> 를 요구한다. 화면에서 메뉴를 감추는 것과는 별개로
 * <b>서버가 막는다</b> — 브라우저에서 이 코드를 고쳐 불러도 403 이다.
 */
export default function RbacPage() {
    const { tokens } = useAuth();
    const accessToken = tokens?.accessToken;

    const [table, setTable] = useState(null);
    const [notice, setNotice] = useState(null);
    const [busy, setBusy] = useState(false);

    async function load(what) {
        setBusy(true);
        setNotice(null);

        if (what === 'roles') {
            const result = await fetchRoles(accessToken);
            setBusy(false);
            if (!result.ok) return fail(result, '역할 조회');
            setTable({
                title: '역할',
                columns: [
                    { key: 'roleId', label: 'ID' },
                    { key: 'roleCode', label: '코드' },
                    { key: 'roleName', label: '이름' },
                    { key: 'description', label: '설명' },
                ],
                rows: result.data ?? [],
            });
            setNotice({
                kind: 'ok',
                text: `어드민 realm 의 역할 ${result.data?.length ?? 0}개. `
                    + '역할은 realm 으로 스코프됩니다 — 포털의 CUSTOMER 역할은 여기 없습니다.',
            });
            return;
        }

        if (what === 'permissions') {
            const result = await fetchPermissions(accessToken);
            setBusy(false);
            if (!result.ok) return fail(result, '권한 조회');
            setTable({
                title: '권한',
                columns: [
                    { key: 'permissionId', label: 'ID' },
                    { key: 'permissionCode', label: '코드' },
                    { key: 'permissionName', label: '이름' },
                    { key: 'category', label: '분류' },
                ],
                rows: result.data ?? [],
            });
            setNotice({
                kind: 'ok',
                text: `어드민 realm 의 권한 ${result.data?.length ?? 0}개. `
                    + '이것은 realm 공통 권한이고, 어드민 콘솔의 화면·URL 제어가 씁니다. '
                    + '업무 서비스의 권한은 여기 없습니다 — 그쪽은 서비스별 역할(client role)로 갈려 있습니다.',
            });
            return;
        }

        const result = await fetchRevision(accessToken);
        setBusy(false);
        if (!result.ok) return fail(result, '리비전 조회');
        setTable(null);
        setNotice({
            kind: 'ok',
            text: `리비전: ${JSON.stringify(result.data)} — realm 전역 정책이 바뀔 때 올라갑니다. `
                + '개별 주체의 역할 부여/회수는 이 값을 올리지 않습니다. 그 변경은 그 사람의 '
                + '다음 재발급 때 반영됩니다.',
        });
    }

    function fail(result, what) {
        setNotice({ kind: 'err', text: result.message ?? `${what} 실패 (${result.status})` });
    }

    return (
        <div className="page">
            <h1>인가 정책</h1>
            <p className="lead">
                어드민 realm 의 역할과 권한. <b>조회만 합니다.</b>
            </p>

            <Notice kind={notice?.kind}>{notice?.text}</Notice>

            <div className="card">
                <div className="row" style={{ marginTop: 0 }}>
                    <button onClick={() => load('roles')} disabled={busy}>역할 목록</button>
                    <button onClick={() => load('permissions')} disabled={busy}>권한 목록</button>
                    <button onClick={() => load('revision')} disabled={busy}>리비전 조회</button>
                </div>

                {table && (
                    <>
                        <h3>{table.title}</h3>
                        {table.rows.length === 0 ? (
                            <p className="hint">결과가 없습니다.</p>
                        ) : (
                            <table className="grid">
                                <thead>
                                <tr>{table.columns.map((c) => <th key={c.key}>{c.label}</th>)}</tr>
                                </thead>
                                <tbody>
                                {table.rows.map((row, i) => (
                                    <tr key={i}>
                                        {table.columns.map((c) => (
                                            <td key={c.key}>
                                                {c.key.endsWith('Code')
                                                    ? <code>{row[c.key] ?? ''}</code>
                                                    : (row[c.key] ?? '')}
                                            </td>
                                        ))}
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        )}
                    </>
                )}
            </div>

            <div className="card muted-card">
                <h2>이 API 들은 auth 자신이 막는다</h2>
                <p className="hint">
                    게이트웨이가 앞단에서 걸러줄 것이라 믿고 두면, auth 를 독립 배포하는 순간
                    인증 없는 관리 API 가 그대로 열립니다. 서명키를 쥔 쪽이 검증도 할 수 있으므로
                    auth 가 직접 확인합니다.
                    <br /><br />
                    이 화면의 메뉴를 감추는 것은 편의일 뿐입니다. 브라우저에서 그 검사를 우회해
                    호출해도 응답은 403 입니다.
                </p>
            </div>
        </div>
    );
}
