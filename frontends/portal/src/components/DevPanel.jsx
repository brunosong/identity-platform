/**
 * 학습용 패널. 기본은 접혀 있다.
 *
 * 이 앱의 절반은 "무슨 요청이 오갔고 토큰에 뭐가 실렸나" 를 보는 것인데, 그것이 화면 한가운데
 * 있으면 서비스가 아니라 개발 도구로 보인다. 그래서 평소엔 접어두고 필요할 때 편다.
 *
 * 감추는 것이 아니라 접는 것이다. 없애면 이 저장소가 설명하려던 것도 같이 사라진다.
 */
export default function DevPanel({ title, children }) {
    return (
        <details className="devpanel">
            <summary>{title}</summary>
            <div className="devpanel-body">{children}</div>
        </details>
    );
}
