package com.brunosong.identityplatform.auth.service.application.identity.event;

/**
 * 신규 직원 계정이 등록됐을 때 발행되는 애플리케이션 이벤트. auth 는 누가 소비하는지 모른 채
 * "이 esntlId 로 직원이 생겼다"만 알린다(BC 디커플). employee 가 이를 소비해
 * 프로필을 만든다.
 *
 * <p>{@code essentialId} 는 auth 가 채번한 subjectId 라 이 이벤트가 나갈 때 이미 확정돼 있다.
 *
 * <p>고객 쪽 {@link SubjectRegisteredEvent} 와 합치지 않았다. 직원은 사번·소속·직위·재직상태를
 * 함께 받아 필드가 겹치지 않는다. 한 이벤트로 묶으면 어느 쪽을 발행하든 절반이 항상 비게 된다.
 *
 * <p>자격증명은 담지 않는다. 직원 로그인은 auth 의 이메일 OTP 라 employee 가 가질 것이 없다.
 */
public record EmployeeRegisteredEvent(
        String essentialId,
        String employeeId,
        String name,
        String email,
        String mobile,
        String positionName,
        String organizationId,
        String statusCode
) {
}
