package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

import com.brunosong.identityplatform.auth.service.domain.identity.LoginSession;

import java.util.Optional;

/**
 * 로그인 세션 저장 드리븐 포트.
 *
 * <p>인가 코드의 저장소와 모양이 닮았지만 읽는 방식이 반대다. 코드는 <b>꺼내면서 지우고</b>
 * 세션은 <b>여러 번 읽는다</b>. 코드는 한 번 쓰고 버리는 표이고, 세션은 앱을 옮길 때마다 다시
 * 보는 값이기 때문이다.
 *
 * <p>만료된 세션도 그대로 내준다. 쓸 수 있는지는 읽은 쪽이 판단한다.
 */
public interface LoginSessionRepository {

    void save(LoginSession session);

    Optional<LoginSession> findById(String sessionId);

    /**
     * 세션을 끊는다. 없는 세션이어도 조용히 지나간다.
     *
     * <p>로그아웃은 "이 세션이 있었나" 를 따질 일이 아니다. 끝난 상태를 만드는 것이 목적이라,
     * 이미 없으면 목적이 이미 이뤄진 것이다. 없다고 오류를 내면 만료된 쿠키를 들고 온 사람이
     * 로그아웃을 못 하게 된다.
     */
    void delete(String sessionId);
}
