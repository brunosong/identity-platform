-- customer-service 기준 스키마.
--
-- auth 와 다른 데이터베이스다. 서비스가 남의 테이블을 직접 읽기 시작하면 나눈 의미가 없어지고,
-- 스키마를 바꿀 때마다 남의 배포를 기다려야 한다.

CREATE TABLE customer_profile
(
    -- auth 가 채번한 subjectId 를 그대로 쓴다. 여기서 따로 번호를 매기면 두 값을 잇는 표가
    -- 또 필요해지고, 그 표가 틀어지는 날이 온다.
    --
    -- auth 의 identity_principal 을 참조하지만 외래키는 걸 수 없다 — 다른 데이터베이스다.
    -- 서비스를 나눈다는 것은 참조 무결성을 DB 에 맡기지 못하게 된다는 뜻이기도 하다.
    -- 그래서 "토큰이 가리키는 주체"만 믿고, 그 토큰은 auth 의 서명으로 확인한다.
    customer_id  varchar(64) PRIMARY KEY,

    name         varchar(100) NOT NULL,
    phone_number varchar(30),
    -- 표시용이다. 로그인 식별자로서의 이메일은 auth 가 소유하므로 여기 값이 낡아도 로그인은 멀쩡하다.
    email        varchar(255),

    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL
);

-- 직원이 고객을 찾을 때 이름/이메일로 훑는다.
CREATE INDEX ix_customer_profile_name ON customer_profile (LOWER(name));
CREATE INDEX ix_customer_profile_email ON customer_profile (LOWER(email));
