-- refresh 토큰의 계보 - "이 로그인에서 지금 살아 있는 refresh 토큰은 하나다".
--
-- 회전(한 번 쓰면 폐기)을 하려면 서버가 "이미 쓴 토큰인가" 를 알아야 한다. refresh 토큰은
-- 서명된 JWT 라 그 자체로는 몇 번을 써도 유효하고, 새어 나가면 만료까지 계속 쓰인다.
-- 그래서 쓴 적이 있는지를 여기에 남긴다.
--
-- 로그인 하나가 행 하나다. 기기를 셋에서 쓰면 로그인이 셋이므로 행도 셋이다.
-- identity_login_session 과는 다른 것이다. 그쪽은 브라우저가 로그인 상태인지 가리는 값이고,
-- 이쪽은 앱이 든 토큰이 그 계보의 현재 것인지 가리는 값이다.

CREATE TABLE identity_refresh_chain
(
    -- 이 계보의 이름. 로그인할 때 하나 나오고, 회전해도 바뀌지 않는다. 토큰의 fid 클레임에 실린다.
    family_id   varchar(36) NOT NULL,

    realm       varchar(20) NOT NULL,

    -- 누구의 계보인가. 토큰의 sub 와 대조한다. 서명이 맞아도 남의 계보를 지목했으면 거절한다.
    subject_id  varchar(36) NOT NULL,

    -- 지금 살아 있는 토큰 하나. 낸 토큰의 jti 가 이것과 다르면 이미 쓴 토큰이고, 그때 행을 지운다.
    -- 지우면 그 계보의 후손이 전부 함께 죽는다. 탈취를 알아채는 자리가 여기다.
    current_jti varchar(36) NOT NULL,

    created_at  timestamptz NOT NULL,

    -- 회전할 때마다 다시 붙는다. 쓰는 동안은 로그인이 안 끊긴다.
    expires_at  timestamptz NOT NULL,

    PRIMARY KEY (family_id),
    CONSTRAINT ck_identity_refresh_chain_realm CHECK (realm IN ('ADMIN', 'PORTAL'))
);

-- identity_principal 로 가는 외래키를 걸지 않는다. identity_login_session 과 같은 이유다.
-- 토큰의 사정이 신원을 지우는 일을 막으면 곤란하다.

-- 만료된 계보를 치우는 쪽은 아직 없다. 만들 때 expires_at 인덱스도 함께 붙인다.
