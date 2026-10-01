package com.brunosong.identityplatform.auth.service.application.identity.ports.in.result;

/**
 * userinfo 가 내주는 값. 권한은 담지 않는다. 권한을 판단하는 것은 각 서비스이고, 화면이 메뉴를
 * 가를 근거로 내주면 그 판단이 앱으로 새어 나간다.
 *
 * @param name        프로필이 없으면 비어 있다
 * @param email       이메일 계정이 없으면(소셜이나 비밀번호만으로 가입) 비어 있다
 * @param phoneNumber 적지 않았으면 비어 있다
 */
public record UserInfo(String subjectId, String name, String email, String phoneNumber) {
}
