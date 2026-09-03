package com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * authz_revision — realm 별 RBAC 리비전(realm 당 한 행). 권한/역할 매핑 변경 시 revision_no++.
 * JWT claim 의 rbacRev 와 비교된다. 한 realm 의 bump 는 다른 realm 토큰에 영향 없다.
 */
@Entity
@Table(name = "authz_revision")
@Getter
@Setter
@NoArgsConstructor
public class AuthzRevisionEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "realm", length = 20)
    private Realm realm;

    @Column(name = "revision_no", nullable = false)
    private Long revisionNo = 1L;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
