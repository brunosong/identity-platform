-- OAuth 클라이언트 등록 - 어떤 앱이 이 realm 에 로그인을 요청할 수 있는가.
--
-- 이 표가 답하는 질문은 사실상 하나다. "로그인이 끝나면 브라우저를 어디로 돌려보내도 되는가".
--
-- 인가 요청(/realms/{realm}/auth)은 돌아갈 주소를 요청자가 적어 보낸다. 그 값을 그대로 믿으면
-- 공격자가 자기 주소를 적어 보내고, 우리가 발급한 code 를 우리 손으로 그쪽에 배달하게 된다.
-- 그래서 미리 등록해 둔 주소하고만 대조한다. 등록되지 않은 주소면 리다이렉트 자체를 하지 않는다.
--
-- 접두어를 새로 뗐다. identity_ 는 사람이고 authz_ 는 권한인데 이것은 앱이라 어느 쪽도 아니다.
-- Keycloak 도 CLIENT 를 USER_ENTITY 와 나란한 자기 표로 둔다.

CREATE TABLE oauth_client
(
    -- 앱 이름이 곧 식별자다. 주소창의 client_id 로 그대로 나가므로 사람이 읽는 이름이어야 한다.
    client_id  varchar(100) NOT NULL,

    -- 이 앱이 상대하는 realm. 앱은 realm 하나에만 속한다 - 고객 앱으로 어드민 로그인을 시작할 수
    -- 없어야 한다. 경로의 realm 과 이 값이 어긋나면 인가 요청을 받지 않는다.
    realm      varchar(20)  NOT NULL,

    -- 껐다 켠다. 행을 지우는 것과 다르다. 지우면 어떤 앱이 있었는지가 사라지고, 같은 client_id 를
    -- 나중에 다른 앱이 가져갈 수 있다.
    enabled    boolean      NOT NULL DEFAULT true,

    created_at timestamptz,
    updated_at timestamptz,

    PRIMARY KEY (client_id),
    CONSTRAINT ck_oauth_client_realm CHECK (realm IN ('ADMIN', 'PORTAL'))
);

-- 시크릿 칸이 없다.
--
-- 브라우저에서 도는 앱은 시크릿을 지킬 수 없다. 번들에 넣으면 개발자 도구로 읽히니 그것은 이미
-- 시크릿이 아니라 공개된 문자열이다. 그래서 여기 등록되는 것은 전부 public client 이고, 시크릿이
-- 하던 일(요청자가 진짜 그 앱인지 확인)은 PKCE 가 요청 하나하나에 대해 대신한다.
--
-- 서버끼리 부르는 client_credentials 같은 것이 필요해지면 그때 시크릿 칸이 생긴다. 지금 미리 두면
-- 비어 있는 칸을 보고 "여기 넣으면 되나" 하게 된다.

-- 어느 realm 의 앱들인지 훑는 조회(관리 화면)가 이 인덱스를 탄다.
CREATE INDEX ix_oauth_client_realm ON oauth_client (realm);

CREATE TABLE oauth_client_redirect_uri
(
    client_id    varchar(100) NOT NULL,

    -- 문자 그대로 대조한다. 와일드카드도 접두어 일치도 없다.
    --
    -- Keycloak 은 http://localhost:5173/* 같은 패턴을 받아주는데, 그것은 편의를 위해 문을 넓힌
    -- 것이고 실제로 사고가 나는 자리다. 경로 한 칸이 열려 있으면 그 앱에 열린 리다이렉트가 하나만
    -- 있어도 code 가 공격자에게 배달된다. 주소가 늘면 패턴을 쓰지 말고 행을 늘린다.
    redirect_uri varchar(500) NOT NULL,

    PRIMARY KEY (client_id, redirect_uri),
    CONSTRAINT fk_oauth_client_redirect_uri_client
        FOREIGN KEY (client_id) REFERENCES oauth_client (client_id)
);
