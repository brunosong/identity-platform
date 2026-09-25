package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

import com.brunosong.identityplatform.auth.service.domain.identity.RefreshChain;

import java.util.Optional;

/**
 * refresh 토큰 계보 저장 드리븐 포트.
 *
 * <p>{@link LoginSessionRepository} 와 모양이 같고 쓰임이 다르다. 세션은 브라우저가 로그인 상태인지
 * 가리고, 이쪽은 앱이 든 refresh 토큰이 그 계보의 현재 것인지 가린다.
 *
 * <p>지우는 자리는 재사용을 발견했을 때 하나다. 만료된 계보를 치우는 쪽은 아직 없다.
 */
public interface RefreshChainRepository {

    void save(RefreshChain chain);

    Optional<RefreshChain> findById(String familyId);

    /**
     * 계보를 끊는다. 없으면 조용히 지나간다. 끝난 상태를 만드는 것이 목적이다.
     *
     * <p><b>부르는 쪽의 트랜잭션과 함께 되돌아가면 안 된다.</b> 재사용을 발견한 요청은 예외로 끝나고,
     * 그 예외가 트랜잭션을 되돌리면 방금 끊은 계보가 되살아난다. 훔친 쪽은 거절당했지만 계보는
     * 그대로 남는 셈이라, 회전이 하려던 일이 아무것도 일어나지 않는다. 구현은 따로 커밋해야 한다.
     */
    void revoke(String familyId);
}
