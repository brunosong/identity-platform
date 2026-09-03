package com.brunosong.identityplatform.auth.service.application.identity.event;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;

/**
 * 신규 주체가 등록됐을 때 발행되는 애플리케이션 이벤트. auth 는 누가 소비하는지 모른 채 "이 식별자로
 * 주체가 생겼다"만 알린다(BC 디커플). customer 가 이를 소비해 프로필을 만든다.
 *
 * <p>{@code subjectId} 는 auth 가 채번한 값이라 이 이벤트가 나갈 때 이미 확정돼 있다. 소비 측은
 * 그 값을 자기 식별자로 받기만 하면 되고, auth 에게 무엇도 돌려주지 않는다.
 *
 * <p>소비 측이 관심 주체만 거른다({@code subjectType}). CUSTOMER 의 subjectId 는 곧 customerUuid 다.
 *
 * <p>프로필 필드(name, phoneNumber)를 싣는 것은 가입 폼이 그것들을 함께 받기 때문이다. 신원만 만들고
 * 프로필은 첫 사용 때 받는 방식으로 가면 이 필드들은 빠진다.
 */
public record SubjectRegisteredEvent(
        String subjectId,
        SubjectType subjectType,
        String email,
        String name,
        String phoneNumber
) {
}
