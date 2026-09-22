package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.Optional;

/**
 * 쿠키로 들어온 세션이 아직 유효한지 보고, 유효하면 누구인지 돌려준다.
 *
 * <p>통합 로그인이 성립하는 자리다. 두 번째 앱의 인가 요청이 들어왔을 때 여기서 값이 나오면
 * 로그인 화면을 띄우지 않는다.
 *
 * <p><b>돌려주는 타입이 비밀번호 확인과 같다.</b> {@link AuthenticatedSubject} 는 로그인 폼이
 * 성공했을 때 나오는 것과 같은 값이다. 사람을 확인한 방법이 비밀번호였든 아까 만든 세션이었든,
 * 그 뒤에 할 일(코드를 발급해 앱으로 돌려보내기)은 똑같아야 한다. 타입이 같으면 그 자리가
 * 두 경로를 구분할 수 없고, 구분할 수 없으면 한쪽만 다르게 동작하는 일도 없다.
 *
 * <p>없는 세션, 만료된 세션, realm 이 어긋난 세션은 모두 빈 값이다. 호출자가 할 일은 어느
 * 쪽이든 같다 - 로그인 화면을 띄운다.
 */
public interface FindLoginSessionUseCase {

    Optional<AuthenticatedSubject> findActive(Realm realm, String sessionId);
}
