package com.brunosong.identityplatform.auth.service.application.authorization.readmodel;

/** 역할-권한 매트릭스의 교차점 1건. */
public record RolePermissionAssignment(Long roleId, Long permissionId) {
}
