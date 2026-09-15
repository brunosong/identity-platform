-- 시스템과 서비스를 갈라 적는다.
--
-- 전에는 authz_permission.client_id 하나가 "이 권한이 어느 서비스의 것인가" 를 담았고, 토큰의
-- aud 에도 그 서비스 이름들이 그대로 나갔다. 그래서 서비스를 하나 붙일 때마다 auth 설정
-- (token.clients.*.audiences)을 고쳐야 했고, 더 나쁜 것은 이미 발급된 토큰에는 새 서비스가
-- 없어서 기존 사용자가 다시 로그인해야 닿았다. 내부 분해가 토큰에 새어나가서 생긴 일이다.
--
-- 이제 aud 는 시스템 하나를 가리킨다. 서비스는 그 시스템 안의 구성요소이고, 토큰의
-- resource_access 칸을 가르는 데만 쓴다. 서비스가 늘어도 aud 는 그대로다.
--
--   realm    PORTAL            서명키가 가른다 (iss)
--   시스템    portal            aud, 단일값
--   서비스    customer-service  resource_access 의 칸
--
-- realm 하나에 시스템이 여럿일 수 있다. 다만 토큰 하나는 시스템 하나를 향한다. 통합 로그인은
-- 세션이 맡는 일이지 aud 에 여럿을 나열해서 될 일이 아니다. 나열하면 가장 약한 시스템이 전체의
-- 보안 수준이 되고, 시스템을 늘릴 때 설정을 고쳐야 하는 문제가 한 층 위로 옮겨갈 뿐이다.

CREATE TABLE authz_service
(
    -- 서비스 이름이 곧 식별자다. 토큰의 resource_access 칸 이름으로 그대로 나가므로
    -- 사람이 읽는 이름이어야 한다.
    service_id varchar(100) NOT NULL,

    realm      varchar(20)  NOT NULL,

    -- 이 서비스가 속한 시스템. 토큰의 aud 가 되는 값이다.
    --
    -- 서비스는 시스템 하나에만 속한다. 둘에 걸치면 그 서비스를 부를 수 있는 토큰이 둘이 되고,
    -- aud 로 경계를 긋는 의미가 사라진다.
    system_id  varchar(100) NOT NULL,

    service_name varchar(200) NOT NULL,
    description  varchar(500),
    use_yn       varchar(1)   NOT NULL DEFAULT 'Y',
    created_at   timestamp(6),
    updated_at   timestamp(6),

    PRIMARY KEY (service_id),
    CONSTRAINT ck_authz_service_realm CHECK (realm IN ('ADMIN', 'PORTAL')),
    CONSTRAINT ck_authz_service_use_yn CHECK (use_yn IN ('Y', 'N'))
);

-- 토큰 발급이 "이 시스템에 속한 서비스들" 로 권한을 좁힌다. 요청마다 타는 경로다.
CREATE INDEX ix_authz_service_system ON authz_service (realm, system_id);

-- authz_permission --------------------------------------------------------
-- client_id 는 두 가지를 동시에 뜻해서 헷갈렸다. token.clients 의 키는 앱(customer-portal)이고
-- 여기 client_id 는 서비스(customer-service)였다. 같은 단어가 다른 것을 가리켰다.
ALTER TABLE authz_permission RENAME COLUMN client_id TO service_id;

ALTER INDEX uk_authz_permission_realm_client_code RENAME TO uk_authz_permission_realm_service_code;

-- 없는 서비스의 권한은 토큰에 실릴 곳이 없다. 오타가 조용히 지나가지 않게 DB 가 붙든다.
--
-- NULL 은 허용한다. 그 자리는 "어드민 콘솔 자신의 권한" 이고, 외래키는 NULL 을 검사하지 않는다.
ALTER TABLE authz_permission
    ADD CONSTRAINT fk_authz_permission_service
        FOREIGN KEY (service_id) REFERENCES authz_service (service_id);
