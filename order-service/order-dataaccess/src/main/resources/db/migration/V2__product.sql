-- 상품. 주문할 때 가격과 재고를 여기서 읽는다.
--
-- 가격을 브라우저에서 받으면 본문을 고쳐 보내는 것만으로 값을 정할 수 있다. 그래서 주문 요청은
-- 상품 번호와 수량만 싣고, 값은 서버가 이 표에서 찾는다.
--
-- 설명, 사양처럼 화면에만 쓰는 값은 여기 두지 않는다. 주문 판단에 쓰이지 않는다.
CREATE TABLE products
(
    -- 사람이 읽을 수 있는 번호다. 포털 화면의 주소(/products/{id})에 그대로 쓰인다.
    product_id varchar(50) PRIMARY KEY,
    name       varchar(200)   NOT NULL,
    price      numeric(15, 2) NOT NULL,
    -- 주문이 들어올 때마다 줄인다. 0 이면 품절이다.
    stock      int            NOT NULL CHECK (stock >= 0)
);

INSERT INTO products (product_id, name, price, stock) VALUES
    ('kb-realforce-45', '무접점 키보드 45g',        259000,  8),
    ('mn-lg-27-4k',     '27인치 4K 모니터',         549000,  3),
    ('hp-sony-wh',      '노이즈 캔슬링 헤드폰',     399000, 14),
    ('ms-mx-master',    '버티컬 무선 마우스',       129000, 21),
    ('nb-a5-dot',       '도트 노트 A5 3권',          21000, 60),
    ('lp-stand-al',     '알루미늄 노트북 거치대',    59000,  0),
    ('cb-usbc-240w',    'USB-C 케이블 240W 2m',      18000, 45),
    ('mg-ceramic-350',  '두꺼운 머그 350ml',         24000, 33);

-- 주문이 어느 상품인지 남긴다. 이름은 바뀔 수 있어 이름만으로는 상품을 다시 찾지 못한다.
-- 이 칸이 생기기 전 주문에는 값이 없어서 NOT NULL 을 걸지 않는다.
ALTER TABLE orders ADD COLUMN product_id varchar(50) REFERENCES products (product_id);
