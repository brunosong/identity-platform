#!/usr/bin/env python3
"""프론트엔드 하나를 자기 포트로 띄우는 개발 서버.

두 앱을 서로 다른 포트로 띄우는 것이 요점이다. 포트가 다르면 브라우저에게는 다른 출처(origin)이고,
그래야 CORS 가 실제로 동작하는지 볼 수 있다 — 실제 배포에서도 두 앱은 다른 도메인에 놓인다.

앱 디렉터리를 루트로 서빙하면서 /shared/ 만 frontends/shared/ 로 함께 내보낸다.
그래서 공유 코드를 앱마다 복사하지 않아도 된다(실제 프로젝트라면 사내 npm 패키지 자리다).

    python frontends/serve.py portal 5173
    python frontends/serve.py backoffice 5174
"""
import functools
import http.server
import os
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))


class Server(http.server.ThreadingHTTPServer):
    # 윈도우는 SO_REUSEADDR 가 켜져 있으면 이미 쓰는 포트에도 바인딩이 "성공"한다.
    # 그러면 요청이 먼저 뜬 서버로 가는데 이쪽은 멀쩡히 뜬 것처럼 보여 원인을 찾기 어렵다.
    # 꺼두면 포트가 겹칠 때 바로 실패한다.
    allow_reuse_address = False


class Handler(http.server.SimpleHTTPRequestHandler):

    def translate_path(self, path):
        clean = path.split('?', 1)[0].split('#', 1)[0]
        # 공유 모듈만 앱 디렉터리 밖에서 가져온다.
        if clean.startswith('/shared/'):
            return os.path.join(ROOT, clean.lstrip('/').replace('/', os.sep))
        return super().translate_path(path)

    def end_headers(self):
        # 개발 중에는 고친 것이 바로 보여야 한다.
        self.send_header('Cache-Control', 'no-store')
        super().end_headers()

    def log_message(self, fmt, *args):
        sys.stderr.write('  %s\n' % (fmt % args))


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 1

    app, port = sys.argv[1], int(sys.argv[2])
    directory = os.path.join(ROOT, app)
    if not os.path.isdir(directory):
        print('그런 앱이 없습니다: %s' % directory)
        return 1

    handler = functools.partial(Handler, directory=directory)
    try:
        server = Server(('127.0.0.1', port), handler)
    except OSError as e:
        print('%d 포트를 쓸 수 없습니다: %s' % (port, e))
        print('다른 것이 그 포트를 쓰고 있습니다. 다른 포트로 띄우고,')
        print('auth-service 의 app.cors.allowed-origins 에 그 출처를 추가하세요.')
        return 1
    print('%s  →  http://localhost:%d  (Ctrl+C 로 종료)' % (app, port))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print()
    return 0


if __name__ == '__main__':
    sys.exit(main())
