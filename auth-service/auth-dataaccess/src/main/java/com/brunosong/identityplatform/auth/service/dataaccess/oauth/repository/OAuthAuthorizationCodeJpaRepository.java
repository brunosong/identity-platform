package com.brunosong.identityplatform.auth.service.dataaccess.oauth.repository;

import com.brunosong.identityplatform.auth.service.dataaccess.oauth.entity.OAuthAuthorizationCodeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OAuthAuthorizationCodeJpaRepository
        extends JpaRepository<OAuthAuthorizationCodeJpaEntity, String> {

    /**
     * 그 코드를 지우고 <b>몇 행을 지웠는지</b> 돌려준다.
     *
     * <p>지운 행 수가 "내가 이 코드를 가져갔다" 는 증표다. 둘이 동시에 같은 코드를 들고 와도
     * 행을 실제로 지운 쪽은 하나뿐이라, 나머지는 0 을 받고 물러난다. 조회해서 있으면 지우는
     * 방식으로는 그 사이에 둘 다 통과할 수 있다.
     */
    @Modifying
    @Query("delete from OAuthAuthorizationCodeJpaEntity c where c.code = :code")
    int deleteByCode(@Param("code") String code);
}
