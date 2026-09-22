-- 로그인 세션 - "이 브라우저에는 이 사람이 로그인해 있다".
--
-- 통합 로그인이 이 표 하나에서 나온다. 두 번째 앱이 인가 요청을 보내면 쿠키에서 세션을 찾고,
-- 살아 있으면 로그인 화면을 띄우지 않고 바로 코드를 내준다.
--
-- 서블릿 세션(메모리)이 아니라 표에 둔다. 메모리에 두면 상태가 그 프로세스에 붙어서, 인스턴스를
-- 둘로 늘리는 순간 "1번에서 로그인했는데 2번이 모른다" 가 된다. 그것을 막으려면 sticky session 이나
-- 세션 복제가 필요해지는데, 둘 다 표 하나보다 비싸다. 인가 코드와 같은 판단이다.

CREATE TABLE identity_login_session
(
    -- 쿠키에 실려 브라우저에 남는 난수. 이것을 쥔 쪽이 그 사람으로 통한다.
    session_id   varchar(64) NOT NULL,

    -- 이 세션이 속한 realm. 쿠키 경로도 /realms/{realm} 로 갈려 있어 브라우저가 알아서 나눠 보내지만,
    -- 값으로도 한 번 더 붙든다. 고객 세션으로 어드민 로그인을 통과하는 길이 없어야 한다.
    realm        varchar(20) NOT NULL,

    -- 누가 로그인해 있는가. 토큰은 나중에 이 사람 앞으로 나간다.
    principal_id varchar(36) NOT NULL,

    created_at   timestamptz NOT NULL,

    -- 절대 만료. 쓸 때마다 연장하지 않는다 - 연장하면 세션을 들여다볼 때마다 쓰기가 생기고,
    -- "언제 끝나는가" 를 코드 여러 곳이 정하게 된다.
    expires_at   timestamptz NOT NULL,

    PRIMARY KEY (session_id),
    CONSTRAINT ck_identity_login_session_realm CHECK (realm IN ('ADMIN', 'PORTAL'))
);

-- identity_principal 로 가는 외래키를 걸지 않는다. 세션은 사람의 일부가 아니라 그 브라우저의
-- 사정이고, 신원을 지우는 일이 "지금 로그인 중인 브라우저가 있으면 막힌다" 가 되면 곤란하다.

-- 만료된 세션을 치우는 쪽은 아직 없다. 만들 때 expires_at 인덱스도 함께 붙인다.
