package com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository;

/** 권한 하나와 그것이 속한 서비스. 서비스별로 묶기 전의 한 행이다. */
public record ServicePermissionRow(String serviceId, String permissionCode) {
}
