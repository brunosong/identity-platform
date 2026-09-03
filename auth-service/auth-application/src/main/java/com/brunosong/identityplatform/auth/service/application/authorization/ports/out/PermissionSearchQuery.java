package com.brunosong.identityplatform.auth.service.application.authorization.ports.out;

import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageResult;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PermissionView;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.List;

/**
 * 권한 검색 포트(SPI) — 조건으로 걸러 읽기 모델을 준다.
 *
 * <p>{@link PermissionQuery}(전체 목록·단건)와 나눈 이유는 수명이 다르기 때문이다. 검색은 조건이
 * 늘고 정렬·랭킹이 붙는 쪽이라, 나중에 색인이나 검색 엔진으로 옮길 때 이 포트만 갈아끼우면 된다.
 *
 * <p>페이지도 이 포트가 만든다. 자를 범위와 건수를 아는 쪽이 여기라, 조립을 응용 계층으로 올리면
 * 두 번 왕복해야 하고 그 사이에 결과가 달라질 수 있다.
 */
public interface PermissionSearchQuery {

    /** 활성 권한 검색(코드/이름/분류 부분일치, 대소문자 무시). keyword 가 비면 전체. */
    List<PermissionView> search(Realm realm, String keyword);

    /** 활성 권한 검색 페이지. */
    PageResult<PermissionView> searchPage(Realm realm, String keyword, PageQuery pageQuery);

    /** 활성 권한의 분류 목록(중복 제거, 정렬) — 검색 필터의 선택지다. */
    List<String> listActiveCategories(Realm realm);
}
