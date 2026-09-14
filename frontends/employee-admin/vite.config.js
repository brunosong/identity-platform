import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 5174 는 auth-service 의 app.cors.allowed-origins 에 이미 들어 있는 출처다.
// 포트를 바꾸면 거기에도 추가해야 브라우저가 요청을 막지 않는다.
//
// 고객 포털(5173)과 포트를 나눈 것이 요점이다. 브라우저에게 포트가 다르면 다른 출처이고,
// 그래야 CORS 가 실제로 동작하는지 볼 수 있다 — 실제 배포에서도 두 앱은 다른 도메인에 놓인다.
//
// 프록시는 두지 않는다. 프록시를 쓰면 브라우저가 보기엔 같은 출처가 되어 CORS 가 사라지는데,
// 그러면 실제 배포에서만 문제가 드러난다.
export default defineConfig({
    plugins: [react()],
    server: {
        port: 5174,
        strictPort: true,   // 포트가 잡혀 있으면 조용히 옮기지 말고 실패할 것
    },
});
