import { useAuth, MANAGE_PERMISSION } from './AuthContext';
import RequireAuth from './RequireAuth';

/**
 * `AUTHZ_MANAGE` 가 있어야 볼 수 있는 화면을 감싼다.
 *
 * 권한이 없을 때 로그인 화면으로 보내지 않는다. 로그인은 이미 했고 <b>권한이 없는 것</b>이라,
 * 다시 로그인시키면 같은 자리로 돌아올 뿐이다. 401 과 403 이 다른 것과 같은 이유다.
 *
 * <p>다시 말하지만 이건 안내다. 서버가 같은 권한을 요구하므로, 이 컴포넌트를 들어내도
 * API 는 403 을 준다.
 */
export default function RequireManage({ children }) {
    const { canManage, permissions } = useAuth();

    return (
        <RequireAuth>
            {canManage ? children : (
                <div className="page narrow">
                    <h1>권한이 없습니다</h1>
                    <p className="lead">
                        로그인은 되어 있지만 <code>{MANAGE_PERMISSION}</code> 권한이 없습니다.
                    </p>

                    <div className="card">
                        <div className="kv">
                            <span>지금 가진 권한</span>
                            <span>
                                {permissions.length === 0
                                    ? <em className="hint">없음</em>
                                    : permissions.map((p) => <span className="chip" key={p}>{p}</span>)}
                            </span>
                        </div>
                        <p className="field-hint">
                            권한은 토큰이 <b>발급될 때</b> 실립니다
                            (<code>resource_access["auth-service"]</code>). 방금 역할을
                            받았다면 이 토큰에는 아직 없습니다 — 홈에서 <b>토큰 재발급</b>을 누르면
                            그 시점의 권한으로 다시 실립니다.
                        </p>
                    </div>

                    <div className="card muted-card">
                        <h2>왜 로그인 화면으로 보내지 않나</h2>
                        <p className="hint">
                            로그인은 이미 했습니다. 없는 것은 권한이라, 다시 로그인해도 같은 자리로
                            돌아올 뿐입니다. 서버가 401 과 403 을 가르는 것과 같은 이유입니다 —
                            401 은 "누구인지 모르겠다", 403 은 "누군지는 알지만 안 된다" 입니다.
                        </p>
                    </div>
                </div>
            )}
        </RequireAuth>
    );
}
