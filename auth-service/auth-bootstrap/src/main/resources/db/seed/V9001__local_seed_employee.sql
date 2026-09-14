-- 로컬 개발용 부트스트랩 직원.
--
-- 닭과 달걀 문제를 푼다: 직원 계정은 관리자만 만들 수 있는데(/api/auth/employee/register 는
-- AUTHZ_MANAGE 권한을 요구한다), 그 권한을 가진 최초의 직원이 없으면 아무도 시작할 수 없다.
-- 실제 운영에서도 최초 관리자는 이렇게 데이터로 심는다.
--
-- 직원은 비밀번호 계정을 갖지 않는다(RegisterEmployeeAccountService 는 EmailAccount 만 만든다).
-- 로그인은 이메일 OTP 로 하고, local 프로파일에서는 메일을 보내지 않고 고정코드 123456 을 쓴다.

-- 신원 ---------------------------------------------------------------------
-- principal_id 를 고정값으로 둔다. 재현 가능한 개발 데이터가 디버깅에 낫다.
INSERT INTO identity_principal (principal_id, subject_id, realm, status, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-0000000000a1', 'admin-esntl-0001', 'ADMIN', 'ACTIVE', now(), now())
ON CONFLICT (realm, subject_id) DO NOTHING;

-- 표시 속성 ------------------------------------------------------------------
-- 가입 API 는 이것을 함께 만든다. 시드는 principal 을 직접 꽂으므로 여기서도 같이 넣어야
-- 관리 화면이 UUID 대신 이름을 보여준다.
INSERT INTO identity_principal_profile (principal_id, name, phone_number, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-0000000000a1', '부트스트랩 관리자', NULL, now(), now())
ON CONFLICT (principal_id) DO NOTHING;

-- 로그인 식별자(이메일 OTP) ---------------------------------------------------
INSERT INTO identity_email_account (email_account_id, principal_id, realm, email, created_at)
VALUES ('00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-0000000000a1',
        'ADMIN', 'admin@example.com', now())
ON CONFLICT (realm, email) DO NOTHING;

-- 역할 부여 ------------------------------------------------------------------
-- 둘을 붙인다.
--   ADMIN                      realm 공통 역할 — 조직에서 맡은 일
--   auth-service:AUTHZ_MANAGE  그 서비스에서 무엇을 열 수 있는가
--
-- 토큰에서도 두 칸으로 갈려 나간다:
--   realm_access.roles                        = ["ADMIN"]
--   resource_access["auth-service"].roles      = ["AUTHZ_MANAGE"]
INSERT INTO authz_subject_role (realm, subject_id, role_id, assigned_at)
SELECT 'ADMIN', 'admin-esntl-0001', r.role_id, now()
FROM authz_role r
WHERE r.realm = 'ADMIN'
  AND (r.client_id IS NULL AND r.role_code = 'ADMIN'
       OR r.client_id = 'auth-service' AND r.role_code = 'AUTHZ_MANAGE')
ON CONFLICT DO NOTHING;
