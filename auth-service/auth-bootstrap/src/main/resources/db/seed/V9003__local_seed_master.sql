-- 로컬 개발용: MASTER realm 의 최초 관리자와 운영 화면 앱.
--
-- Keycloak 을 처음 띄우면 master 에 admin 계정 하나가 있는 것과 같은 자리다. MASTER 는 셀프 가입이
-- 닫혀 있어 누군가는 데이터로 심어야 시작할 수 있다.
--
-- 로그인은 아이디 admin, 비밀번호 admin. 직원과 달리 비밀번호 계정을 준다. Keycloak 관리자도
-- 비밀번호로 들어오고, 메일 없이 바로 로그인해 볼 수 있어야 해서다.

-- 신원 ---------------------------------------------------------------------
INSERT INTO identity_principal (principal_id, subject_id, realm, status, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-0000000000b1', 'master-admin', 'MASTER', 'ACTIVE', now(), now())
ON CONFLICT (realm, subject_id) DO NOTHING;

INSERT INTO identity_principal_profile (principal_id, name, phone_number, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-0000000000b1', 'MASTER 관리자', NULL, now(), now())
ON CONFLICT (principal_id) DO NOTHING;

-- 로그인 식별자(비밀번호) -------------------------------------------------------
-- 해시는 BCryptPasswordEncoder 로 만든 "admin".
INSERT INTO identity_password_account (password_account_id, principal_id, realm, login_id, password_hash, created_at)
VALUES ('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-0000000000b1', 'MASTER', 'admin',
        '$2a$10$bCHrPCXbt4RfTFiG9ZLOqeV.YH/BUb4BBU7trmQxgcoCpO385VAXi', now())
ON CONFLICT (realm, login_id) DO NOTHING;

-- 역할 ---------------------------------------------------------------------
-- 권한은 아직 붙이지 않는다. MASTER 관리자가 무엇을 할 수 있는지는 운영 화면을 막는 조각에서 정한다.
INSERT INTO authz_role (realm, role_code, role_name, description, use_yn, created_at, updated_at)
VALUES ('MASTER', 'ADMIN', 'Master administrator', 'auth 운영자', 'Y', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO authz_subject_role (realm, subject_id, role_id, assigned_at)
SELECT 'MASTER', 'master-admin', r.role_id, now()
FROM authz_role r
WHERE r.realm = 'MASTER' AND r.role_code = 'ADMIN'
ON CONFLICT DO NOTHING;

-- 운영 화면 앱 ----------------------------------------------------------------
-- Keycloak 의 security-admin-console 자리. 돌아갈 주소는 운영 화면이 로그인 결과를 받을 곳이다.
INSERT INTO oauth_client (client_id, client_name, realm, enabled, created_at, updated_at)
VALUES ('auth-console', '운영 화면', 'MASTER', true, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO oauth_client_redirect_uri (client_id, redirect_uri)
VALUES ('auth-console', 'http://localhost:8080/page/login/callback')
ON CONFLICT DO NOTHING;
