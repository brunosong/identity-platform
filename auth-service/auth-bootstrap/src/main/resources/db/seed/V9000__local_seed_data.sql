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
INSERT INTO authz_role (realm, role_code, role_name, description, use_yn, created_at, updated_at)
VALUES
    -- 신규 고객이 가입 시 자동으로 받는 역할. 코드가 상수로 박혀 있다(CustomerDefaultRoleGrantListener).
    ('CUSTOMER', 'CUSTOMER', 'Customer', 'Default role granted on customer sign-up', 'Y', now(), now()),
    -- 직원 등록·RBAC 편집 API 를 부를 수 있는 역할. 직원은 운영자가 만들어야 하므로 부트스트랩용이다.
    ('EMPLOYEE', 'ADMIN', 'Administrator', 'Can edit roles, permissions and URL rules', 'Y', now(), now())
ON CONFLICT (realm, role_code) DO NOTHING;

-- 권한 --------------------------------------------------------------------
INSERT INTO authz_permission (realm, category, permission_code, permission_name, description, use_yn, created_at, updated_at)
VALUES
    -- authorization.manage-permission 기본값과 같은 코드여야 한다.
    ('EMPLOYEE', 'AUTHZ', 'AUTHZ_MANAGE', 'Manage authorization', 'Edit roles/permissions/URL rules', 'Y', now(), now())
ON CONFLICT (realm, permission_code) DO NOTHING;

-- 역할-권한 ---------------------------------------------------------------
INSERT INTO authz_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM authz_role r
         JOIN authz_permission p ON p.realm = r.realm
WHERE r.realm = 'EMPLOYEE'
  AND r.role_code = 'ADMIN'
  AND p.permission_code = 'AUTHZ_MANAGE'
ON CONFLICT DO NOTHING;
