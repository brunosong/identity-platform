/** 결과 알림. kind 는 ok / err / info. */
export default function Notice({ kind = 'info', children }) {
    if (!children) return null;
    return <div className={`notice ${kind}`}>{children}</div>;
}
