package com.brunosong.identityplatform.auth.service.dataaccess.support;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@code CHAR(1)} 의 Y/N 과 boolean 사이를 옮기는 컨버터.
 *
 * <p>스키마는 Y/N 을 그대로 두되(기존 데이터·조회 도구가 그 표기를 쓴다), 자바 쪽에는 boolean 만
 * 보이게 한다. "Y" 문자열이 엔티티 밖으로 새면 비교가 코드마다 달라진다 —
 * {@code "Y".equals(x)} 와 {@code !"N".equals(x)} 는 null 에서 결과가 갈린다.
 */
@Converter
public class YnBooleanConverter implements AttributeConverter<Boolean, String> {

    private static final String YES = "Y";
    private static final String NO = "N";

    @Override
    public String convertToDatabaseColumn(Boolean attribute) {
        return Boolean.TRUE.equals(attribute) ? YES : NO;
    }

    @Override
    public Boolean convertToEntityAttribute(String dbData) {
        return YES.equalsIgnoreCase(dbData);
    }
}
