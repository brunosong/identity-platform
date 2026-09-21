package com.brunosong.identityplatform.auth.service.application.oauth.ports.out;

import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationCode;

import java.util.Optional;

/**
 * 인가 코드 저장 드리븐 포트.
 *
 * <p>읽는 방법이 하나뿐이고 그것이 <b>꺼내면서 지우는</b> 일이다. 조회와 삭제를 나눠 두면
 * 호출자가 삭제를 잊을 수 있고, 잊은 그 순간 코드는 몇 번이고 쓸 수 있는 값이 된다.
 * "한 번만 쓴다" 를 규율이 아니라 메서드 하나로 만든다.
 */
public interface AuthorizationCodeRepository {

    void save(AuthorizationCode code);

    /**
     * 그 코드를 꺼내고 지운다. 없으면 빈 값이다.
     *
     * <p>만료 여부는 보지 않는다. 만료된 코드도 꺼내면서 치우고, 쓸 수 있는지는 호출자가 판단한다 -
     * 여기서 걸러 버리면 "만료됐다" 와 "그런 코드가 없다" 가 구분되지 않는다.
     *
     * <p>같은 코드로 동시에 두 번 들어와도 하나만 받아간다. 지운 행이 있을 때만 값을 내준다.
     */
    Optional<AuthorizationCode> consume(String code);
}
