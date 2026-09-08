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
INSERT INTO identity_principal (principal_id, subject_id, subject_type, status, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-0000000000a1', 'admin-esntl-0001', 'EMPLOYEE', 'ACTIVE', now(), now())
ON CONFLICT (subject_type, subject_id) DO NOTHING;

-- 로그인 식별자(이메일 OTP) ---------------------------------------------------
INSERT INTO identity_email_account (email_account_id, principal_id, subject_type, email, created_at)
VALUES ('00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-0000000000a1',
        'EMPLOYEE', 'admin@example.com', now())
ON CONFLICT (subject_type, email) DO NOTHING;

-- 역할 부여 ------------------------------------------------------------------
-- V9000 이 만든 EMPLOYEE/ADMIN 역할을 붙인다. 그 역할이 AUTHZ_MANAGE 권한을 갖고 있어
-- 이 계정으로 로그인하면 RBAC 관리 API 와 직원 등록 API 를 부를 수 있다.
INSERT INTO authz_subject_role (realm, subject_id, role_id, assigned_at)
SELECT 'EMPLOYEE', 'admin-esntl-0001', r.role_id, now()
FROM authz_role r
WHERE r.realm = 'EMPLOYEE'
  AND r.role_code = 'ADMIN'
ON CONFLICT DO NOTHING;
