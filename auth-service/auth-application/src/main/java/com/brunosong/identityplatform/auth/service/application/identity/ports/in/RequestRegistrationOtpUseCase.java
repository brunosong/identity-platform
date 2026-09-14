package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 가입용 이메일 인증번호 발송.
 *
 * <p>로그인용({@link RequestEmailOtpUseCase})과 <b>조건이 정반대</b>다. 로그인은 등록된 주소에만
 * 보내고, 가입은 등록되지 <i>않은</i> 주소에만 보낸다. 그래서 유스케이스를 따로 둔다 —
 * 한 유스케이스에 플래그를 받으면 부르는 쪽이 그 플래그를 틀리는 날이 온다.
 */
public interface RequestRegistrationOtpUseCase {

    /** 이미 등록된 주소면 조용히 끝난다. 응답으로는 구분되지 않는다(계정 열거 방지). */
    void request(Realm realm, String email);
}
