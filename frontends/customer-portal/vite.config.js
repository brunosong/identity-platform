import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 5173 은 auth-service 의 app.cors.allowed-origins 에 이미 들어 있는 출처다.
// 포트를 바꾸면 거기에도 추가해야 브라우저가 요청을 막지 않는다.
//
// 프록시를 두지 않는 것은 의도적이다. 프록시를 쓰면 브라우저가 보기엔 같은 출처가 되어
// CORS 가 사라지는데, 그러면 실제 배포(다른 도메인)에서만 문제가 드러난다.
export default defineConfig({
    plugins: [react()],
    server: {
        port: 5173,
        strictPort: true,   // 포트가 잡혀 있으면 조용히 옮기지 말고 실패할 것
    },
});
