-- 인가 코드 - 로그인이 끝난 직후 앱에게 건네는 1회용 증서.
--
-- 수명이 1분인 값을 표에 적는 것이 어색해 보이는 자리다. 메모리에 두면 더 빠르고 저절로
-- 사라진다. 그런데 이 서비스는 여러 대로 뜨고, 로그인을 처리한 인스턴스와 토큰을 바꿔주는
-- 인스턴스가 같다는 보장이 없다. 메모리에 두면 그 순간 "가끔 로그인이 안 되는" 서비스가 된다.
--
-- 같은 이유로 이메일 인증번호(identity_email_otp)도 표에 있다. 이 저장소는 이미 그 답을 골랐다.

CREATE TABLE oauth_authorization_code
(
    -- 주소창에 실려 나가는 난수. 32바이트를 base64url 로 적으면 43글자다.
    code           varchar(64)  NOT NULL,

    realm          varchar(20)  NOT NULL,

    -- 발급을 시작한 앱. 토큰을 바꾸러 온 쪽이 같은 앱인지 대조한다.
    client_id      varchar(100) NOT NULL,

    -- 시작할 때 적혀 온 주소. 교환 요청도 같은 값을 내야 한다(RFC 6749 4.1.3).
    redirect_uri   varchar(500) NOT NULL,

    -- PKCE 검증값의 해시. 원본은 앱만 쥐고 있고, 이 코드를 주운 쪽은 모른다.
    code_challenge varchar(128) NOT NULL,

    -- 누가 로그인했는가. 토큰은 이 사람 앞으로 나간다.
    principal_id   varchar(36)  NOT NULL,

    scope          varchar(200),

    -- id_token 에 그대로 실어 보낼 값.
    nonce          varchar(200),

    expires_at     timestamptz  NOT NULL,
    created_at     timestamptz,

    PRIMARY KEY (code),
    CONSTRAINT ck_oauth_authorization_code_realm CHECK (realm IN ('ADMIN', 'PORTAL'))
);

-- oauth_client 로 가는 외래키를 걸지 않는다. 이 행은 1분 뒤에 없어질 값이고, 외래키를 걸면
-- 앱을 지우는 일이 "지금 로그인 중인 사람이 있으면 막힌다" 가 된다. 앱과 주소의 대조는
-- 교환 시점에 코드에 적힌 값으로 한다.

-- 쓴 코드는 꺼내면서 지운다. 그래도 아무도 바꾸러 오지 않은 코드는 만료된 채 남는다.
-- 치우는 쪽은 아직 없다. 만들 때 expires_at 인덱스도 함께 붙인다 - 지금 미리 두면 아무도
-- 타지 않는 인덱스가 쓰기마다 갱신될 뿐이다.
