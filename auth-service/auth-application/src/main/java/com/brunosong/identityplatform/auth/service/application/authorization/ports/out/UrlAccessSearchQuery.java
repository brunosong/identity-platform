package com.brunosong.identityplatform.auth.service.application.authorization.ports.out;

import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageResult;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.UrlAccessView;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * URL 접근 규칙 검색 포트(SPI) — 조건으로 걸러 읽기 모델을 준다.
 *
 * <p>{@link UrlAccessQuery}(전체 목록·단건·역방향·판정 규칙)와 나눈 이유는 수명이 다르기 때문이다.
 * 검색은 조건이 늘고 정렬·랭킹이 붙는 쪽이라, 나중에 색인이나 검색 엔진으로 옮길 때 이 포트만
 * 갈아끼우면 된다.
 *
 * <p>페이지도 이 포트가 만든다. 자를 범위와 건수를 아는 쪽이 여기라, 조립을 응용 계층으로 올리면
 * 두 번 왕복해야 하고 그 사이에 결과가 달라질 수 있다.
 */
public interface UrlAccessSearchQuery {

    /**
     * 활성 URL 규칙 검색 페이지.
     *
     * @param keyword  URL 패턴 또는 매핑 권한코드 부분일치(대소문자 무시). 비면 전체.
     * @param category {@code PAGE} 는 페이지 호출 URL, 그 외는 매핑된 권한의 분류. 비면 전체.
     */
    PageResult<UrlAccessView> searchPage(Realm realm, String keyword, String category, PageQuery pageQuery);
}
