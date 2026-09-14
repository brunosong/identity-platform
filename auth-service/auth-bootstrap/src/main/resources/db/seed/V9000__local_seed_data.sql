-- 로컬 개발용 기준 데이터.
--
-- 운영 마이그레이션(db/migration)과 분리된 경로(db/seed)에 둔다. local 프로파일만 이 경로를 읽으므로
-- 운영 DB 에는 들어가지 않는다. 버전 번호를 9000 부터 쓰는 것은 앞으로 늘어날 운영 마이그레이션과
-- 번호가 겹치지 않게 하기 위해서다.
--
-- 왜 필요한가: 고객 가입은 CustomerDefaultRoleGrantListener 가 CUSTOMER 역할을 부여하며 끝나고,
-- 그 역할이 없으면 가입 트랜잭션이 통째로 롤백된다(역할 없는 계정은 로그인해도 아무 데도 못 간다).
-- 즉 이 시드가 없으면 로컬에서 가입 자체가 안 된다.
--
-- role_id / permission_id 는 identity 라 넣지 않는다 — 명시하면 시퀀스가 뒤처져 이후 삽입이 충돌한다.
-- 이미 있는 DB 에 다시 돌려도 되도록 ON CONFLICT 로 둔다(Flyway 는 한 번만 돌리지만, 손으로 실행할 때).

-- realm 공통 역할 ----------------------------------------------------------
-- client_id 가 NULL 이다. 영역 전체에서 뜻이 같은 역할이고, 어드민 콘솔의 화면·URL 제어가
-- 여기 붙은 권한(authz_permission)을 쓴다.
INSERT INTO authz_role (realm, client_id, role_code, role_name, description, use_yn, created_at, updated_at)
VALUES
    -- 신규 고객이 가입 시 자동으로 받는 역할. 코드가 상수로 박혀 있다(CustomerDefaultRoleGrantListener).
    -- realm 은 PORTAL 이고 role_code 는 CUSTOMER 다 — 영역과 역할은 다른 것이다.
    ('PORTAL', NULL, 'CUSTOMER', 'Customer', 'Default role granted on customer sign-up', 'Y', now(), now()),
    -- 직원의 기본 역할. 조직에서 맡은 일을 가리킨다 — 무엇을 열 수 있는지는 아래 client role 이 정한다.
    ('ADMIN', NULL, 'ADMIN', 'Administrator', 'Employee administrator', 'Y', now(), now())
ON CONFLICT DO NOTHING;

-- 서비스별 역할(client role) ------------------------------------------------
-- client_id 가 있으면 그 서비스의 역할이고, <b>역할 코드 자체가 권한</b>이다.
-- auth 는 이름만 보관한다 — AUTHZ_MANAGE 가 무엇을 여는지는 auth-service 가 자기 코드로 정한다.
--
-- 서비스가 늘면 여기에 한 줄씩 더한다. 어휘가 서비스로 갈려 있어 이름이 부딪히지 않는다:
-- order-service 의 READ 와 customer-service 의 READ 는 서로 다른 행이다.
INSERT INTO authz_role (realm, client_id, role_code, role_name, description, use_yn, created_at, updated_at)
VALUES
    -- authorization.manage-permission 기본값과 같은 코드여야 한다.
    ('ADMIN', 'auth-service', 'AUTHZ_MANAGE', 'Manage authorization',
     'Edit roles/permissions/URL rules, register employees', 'Y', now(), now()),
    -- customer-service 가 검사하는 역할. 가입 시 자동으로 부여된다
    -- (CustomerDefaultRoleGrantListener). 이 이름이 어떤 URL 을 여는지는 customer-service 의
    -- SecurityConfiguration 이 정한다 — auth 는 이름만 안다.
    ('PORTAL', 'customer-service', 'PROFILE_READ', 'Read own profile',
     'Access /api/customers/me', 'Y', now(), now())
ON CONFLICT DO NOTHING;
