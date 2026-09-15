package com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity;

import com.brunosong.identityplatform.auth.service.dataaccess.support.YnBooleanConverter;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * authz_service. 서비스가 어느 시스템에 속하는지.
 *
 * <p>여기가 <b>시스템과 서비스를 가르는 자리</b>다. 시스템은 토큰의 {@code aud} 가 되고,
 * 서비스는 {@code resource_access} 의 칸이 된다.
 *
 * <pre>
 * service_id         system_id
 * customer-service   portal   ─┐  aud = ["portal"] 하나
 * order-service      portal   ─┘  resource_access 는 서비스별로 두 칸
 * </pre>
 *
 * <p>서비스를 붙이는 일이 이 표에 한 줄이다. {@code aud} 는 그대로라서 auth 설정을 고칠 필요도,
 * 이미 발급된 토큰을 버릴 필요도 없다.
 *
 * <p>토큰 발급이 요청마다 이 표를 탄다. 권한을 "이 시스템에 속한 서비스들" 로 좁히는 조인이다.
 */
@Entity
@Table(name = "authz_service")
@Getter
@Setter
@NoArgsConstructor
public class AuthzServiceEntity {

    /** 토큰의 {@code resource_access} 칸 이름으로 그대로 나간다. */
    @Id
    @Column(name = "service_id", length = 100)
    private String serviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "realm", nullable = false, length = 20)
    private Realm realm;

    /** 토큰의 {@code aud} 가 되는 값. 서비스는 시스템 하나에만 속한다. */
    @Column(name = "system_id", nullable = false, length = 100)
    private String systemId;

    @Column(name = "service_name", nullable = false, length = 200)
    private String serviceName;

    @Column(name = "description", length = 500)
    private String description;

    @Convert(converter = YnBooleanConverter.class)
    @Column(name = "use_yn", nullable = false, length = 1)
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
