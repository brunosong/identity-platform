-- order-service 기준 스키마.
--
-- auth 와도, customer 와도 다른 데이터베이스다. 서비스가 남의 테이블을 직접 읽기 시작하면
-- 나눈 의미가 없어지고, 스키마를 바꿀 때마다 남의 배포를 기다려야 한다.

-- 표 이름이 order 가 아니라 orders 인 것은 ORDER 가 SQL 예약어라서다. 따옴표로 감싸면 쓸 수는
-- 있지만 그러면 손으로 질의할 때마다 따옴표를 붙여야 한다.
CREATE TABLE orders
(
    -- 이 서비스가 채번한다. 프로필과 달리 주문은 사람당 여럿이라 주체 식별자를 그대로 열쇠로
    -- 쓸 수 없다. 그 대신 이 값이 요청 경로로 들어오므로 조회할 때 customer_id 를 함께 건다.
    order_id     varchar(36) PRIMARY KEY,

    -- auth 가 채번한 subjectId. customer-service 의 customer_profile.customer_id 와 같은 값이지만
    -- 두 서비스는 서로의 표를 보지 않는다. 같은 사람을 가리키는 이유는 토큰이 하나이기 때문이다.
    --
    -- 외래키는 걸 수 없다. 다른 데이터베이스다.
    customer_id  varchar(64)    NOT NULL,

    product_name varchar(200)   NOT NULL,
    quantity     int            NOT NULL,
    -- 돈은 정수배 단위로 다룬다. double 로 두면 반올림이 조용히 어긋난다.
    amount       numeric(15, 2) NOT NULL,
    -- 이름으로 저장한다. 순서로 저장하면 상태를 하나 끼워 넣는 날 기존 행의 뜻이 통째로 바뀐다.
    status       varchar(20)    NOT NULL,

    created_at   timestamptz    NOT NULL
);

-- 조회는 언제나 customer_id 로 좁힌다. 목록은 최근 것부터 보여주므로 created_at 까지 묶는다.
CREATE INDEX ix_orders_customer ON orders (customer_id, created_at DESC);
