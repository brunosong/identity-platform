-- 로컬 개발용: 직원 관리자 앱.
--
-- 어드민 realm 의 첫 앱이다. 앞서 등록한 둘(portal, shop-web)은 모두 고객 realm 이라,
-- 이 앱이 붙어야 "realm 이 다르면 세션도 갈린다" 를 눈으로 볼 수 있다. 포털에 로그인해 둔
-- 브라우저로 이 앱에 들어가면 로그인 화면이 다시 떠야 한다.
--
-- 직원은 비밀번호 계정을 갖지 않는다(Realm.ADMIN 은 이메일 인증번호 가입만 연다). 그래서
-- 이 앱의 로그인은 로그인 화면의 인증번호 폼으로만 된다. 시드 계정은 admin@example.com 이다
-- (V9001__local_seed_employee.sql).

INSERT INTO oauth_client (client_id, realm, enabled, created_at, updated_at)
VALUES ('backoffice', 'ADMIN', true, now(), now())
ON CONFLICT DO NOTHING;

-- 두 줄인 이유는 로그아웃 때문이다. 로그아웃 뒤 돌아갈 주소도 이 목록으로 대조하는데,
-- 콜백 주소로 돌려보내면 code 가 없어 그 화면이 실패로 보인다. 홈 주소를 함께 등록해 둔다.
-- 목록을 갈라야 하는 이유는 V9005 주석에 적어 뒀다.
INSERT INTO oauth_client_redirect_uri (client_id, redirect_uri)
VALUES ('backoffice', 'http://localhost:5174/login/callback'),
       ('backoffice', 'http://localhost:5174/')
ON CONFLICT DO NOTHING;
