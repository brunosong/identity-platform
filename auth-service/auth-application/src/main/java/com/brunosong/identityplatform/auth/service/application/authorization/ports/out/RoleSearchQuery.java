package com.brunosong.identityplatform.auth.service.application.authorization.ports.out;

import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.List;

/**
 * 역할 검색 포트(SPI) — 조건으로 걸러 읽기 모델을 준다.
 *
 * <p>{@link RoleQuery}(전체 목록·단건)와 나눈 이유는 수명이 다르기 때문이다. 검색은 조건이 늘고
 * 정렬·랭킹이 붙는 쪽이라, 나중에 색인이나 검색 엔진으로 옮길 때 이 포트만 갈아끼우면 된다.
 */
public interface RoleSearchQuery {

    /** 활성 역할 검색(코드/이름 부분일치, 대소문자 무시). keyword 가 비면 전체. */
    List<RoleView> search(Realm realm, String keyword);
}
