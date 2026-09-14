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

-- 역할 --------------------------------------------------------------------
-- 역할은 "이 사람이 조직에서 맡은 일" 이다. 서비스로 갈리지 않는다.
INSERT INTO authz_role (realm, role_code, role_name, description, use_yn, created_at, updated_at)
VALUES
    -- 신규 고객이 가입 시 자동으로 받는 역할. 코드가 상수로 박혀 있다(CustomerDefaultRoleGrantListener).
    -- realm 은 PORTAL 이고 role_code 는 CUSTOMER 다 — 영역과 역할은 다른 것이다.
    ('PORTAL', 'CUSTOMER', 'Customer', 'Default role granted on customer sign-up', 'Y', now(), now()),
    -- 직원의 관리자 역할. 직원은 운영자가 만들어야 하므로 부트스트랩용이다.
    ('ADMIN', 'ADMIN', 'Administrator', 'Employee administrator', 'Y', now(), now())
ON CONFLICT DO NOTHING;

-- 권한 --------------------------------------------------------------------
-- 권한은 서비스로 갈린다(client_id). auth 는 이름만 보관하고, 그 이름이 어떤 URL 을 여는지는
-- 그 서비스가 자기 코드로 정한다 — 서비스가 엔드포인트를 늘려도 auth 를 배포하지 않는다.
--
-- 어휘가 서비스로 갈려 있어 이름이 부딪히지 않는다: order-service 의 READ 와
-- customer-service 의 READ 는 서로 다른 행이다.
INSERT INTO authz_permission (realm, client_id, category, permission_code, permission_name, description, use_yn, created_at, updated_at)
VALUES
    -- authorization.manage-permission 기본값과 같은 코드여야 한다.
    -- 어드민 콘솔이 상대하는 서비스가 auth 자신이라 client_id 가 auth-service 다.
    ('ADMIN', 'auth-service', 'AUTHZ', 'AUTHZ_MANAGE', 'Manage authorization',
     'Edit roles/permissions/URL rules, register employees', 'Y', now(), now()),
    -- customer-service 가 요구하는 권한. 그 서비스의 SecurityConfiguration 이
    -- /api/customers/** 에 이것을 건다.
    ('PORTAL', 'customer-service', 'CUSTOMER', 'PROFILE_READ', 'Read own profile',
     'Access /api/customers/me', 'Y', now(), now())
ON CONFLICT DO NOTHING;

-- 역할-권한 ---------------------------------------------------------------
-- 여기가 "그 역할이 무엇을 할 수 있나" 다. 배포 없이 관리 화면에서 바뀌는 자리이기도 하다.
INSERT INTO authz_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM authz_role r
         JOIN authz_permission p ON p.realm = r.realm
WHERE (r.realm = 'ADMIN'  AND r.role_code = 'ADMIN'    AND p.permission_code = 'AUTHZ_MANAGE')
   OR (r.realm = 'PORTAL' AND r.role_code = 'CUSTOMER' AND p.permission_code = 'PROFILE_READ')
ON CONFLICT DO NOTHING;
