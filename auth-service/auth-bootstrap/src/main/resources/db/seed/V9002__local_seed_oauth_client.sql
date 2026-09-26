-- 로컬 개발용: 인가 요청을 시작할 수 있는 앱.
--
-- 이 행이 없으면 로컬에서 /realms/{realm}/auth 는 아무 요청도 받지 않는다. 등록되지 않은 앱이기
-- 때문이다. 운영에는 앱을 붙이는 사람이 등록 화면으로 넣는다.
--
-- client_id 는 사람이 짓지 않는다. realm 접두어에 난수 12자를 붙인 값이다. 사람이 지으면
-- portal, admin, web 같은 흔한 이름으로 몰리고, 실제로 backoffice 라는 앱 이름이 ADMIN 시스템
-- 이름(access 토큰의 aud)과 겹쳐 그 앱의 id_token 이 access 토큰으로 통과할 수 있었다.
-- 접두어는 로그에서 어느 realm 의 앱인지 알아보라고 붙인다. 값 자체는 비밀이 아니다.
-- 등록 화면으로 넣으면 같은 모양으로 발급된다(OAuthClient.register). 시드는 한 번 뽑은 값을
-- 고정해 둔다. 프론트 설정이 이 값을 그대로 적고 있다.
--
--   portal-17kqqi85h2ks   포털        frontends/portal
--   portal-1rtojq5fqz7u   쇼핑몰      frontends/shop
--   admin-ln4efwmg0tee    백오피스    frontends/backoffice
--
-- 셋 다 시크릿이 없는 public client 다. 브라우저에서 도는 앱이라 시크릿을 지킬 수 없다.

-- 앱 -----------------------------------------------------------------------
-- 포털과 쇼핑몰은 같은 realm 이다. 포털에서 로그인한 브라우저로 쇼핑몰 로그인을 누르면 로그인
-- 화면이 뜨지 않아야 한다(통합 로그인). 백오피스는 realm 이 달라 세션이 갈리는 것이 정상이다.
-- 백오피스 로그인은 인증번호 폼으로만 된다. 직원은 비밀번호 계정이 없다(V9001).
INSERT INTO oauth_client (client_id, client_name, realm, enabled, created_at, updated_at)
VALUES ('portal-17kqqi85h2ks', '포털', 'PORTAL', true, now(), now()),
       ('portal-1rtojq5fqz7u', '쇼핑몰', 'PORTAL', true, now(), now()),
       ('admin-ln4efwmg0tee', '백오피스', 'ADMIN', true, now(), now())
ON CONFLICT DO NOTHING;

-- 돌아갈 주소 ----------------------------------------------------------------
-- 앱마다 둘이다. 하나는 로그인 콜백이고, 하나는 로그아웃 뒤 돌아갈 홈이다.
--
-- 홈을 여기 넣는 대가가 있다. 로그아웃 뒤 주소도 이 목록으로 대조하는데, 이 목록에 있는 주소는
-- 인가 코드도 받을 수 있다. 홈이 code 를 받아도 하는 일이 없으니 지금은 문제가 아니지만, OIDC 가
-- 로그아웃용 주소를 따로 등록하게 하는 이유가 이것이다. 목록을 가르면 홈 줄이 그쪽으로 옮겨간다.
--
-- 포털 콜백이 /callback 이 아닌 것은 그 경로를 구글 콜백이 이미 쓰고 있기 때문이다.
INSERT INTO oauth_client_redirect_uri (client_id, redirect_uri)
VALUES ('portal-17kqqi85h2ks', 'http://localhost:5173/login/callback'),
       ('portal-17kqqi85h2ks', 'http://localhost:5173/'),
       ('portal-1rtojq5fqz7u', 'http://localhost:5176/callback.html'),
       ('portal-1rtojq5fqz7u', 'http://localhost:5176/'),
       ('admin-ln4efwmg0tee', 'http://localhost:5174/login/callback'),
       ('admin-ln4efwmg0tee', 'http://localhost:5174/')
ON CONFLICT DO NOTHING;
