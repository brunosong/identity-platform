package com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command;

/**
 * 등록된 URL 리소스의 설명과 정렬 순서를 고친다.
 */
public record DescribeUrlAccessCommand(Long urlAccessId, String description, int sortOrder) {
}
