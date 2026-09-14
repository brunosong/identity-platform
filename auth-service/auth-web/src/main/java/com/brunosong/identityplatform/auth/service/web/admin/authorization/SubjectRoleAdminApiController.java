package com.brunosong.identityplatform.auth.service.web.admin.authorization;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.AssignSubjectRolesUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.FindRolesUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 주체-역할 배정 운영 API. */
@RestController
@RequestMapping("/api/admin/rbac/subjects")
@RequiredArgsConstructor
public class SubjectRoleAdminApiController {

    private final AssignSubjectRolesUseCase assignSubjectRoles;
    private final FindRolesUseCase findRoles;
    private final RbacAdminAccess access;

    @GetMapping("/{subjectId}/roles")
    public List<RoleView> ofSubject(HttpServletRequest request,
                                    @RequestParam Realm realm,
                                    @PathVariable String subjectId) {
        access.require(request);
        return findRoles.ofSubject(realm, subjectId);
    }

    /** 여러 주체의 역할을 한 번에 조회한다(목록 화면의 역할 컬럼용). */
    @PostMapping("/roles")
    public Map<String, List<RoleView>> ofSubjects(HttpServletRequest request,
                                                  @RequestParam Realm realm,
                                                  @Valid @RequestBody SubjectIdsRequest body) {
        access.require(request);
        Map<String, List<RoleView>> bySubject = new LinkedHashMap<>();
        for (String subjectId : body.subjectIds()) {
            bySubject.put(subjectId, findRoles.ofSubject(realm, subjectId));
        }
        return bySubject;
    }

    @PutMapping("/{subjectId}/roles")
    public ResponseEntity<Void> assign(HttpServletRequest request,
                                       @RequestParam Realm realm,
                                       @PathVariable String subjectId,
                                       @Valid @RequestBody RoleIdsRequest body) {
        access.require(request);
        assignSubjectRoles.assign(realm, subjectId, body.roleIds());
        return ResponseEntity.noContent().build();
    }

    public record SubjectIdsRequest(@NotNull List<String> subjectIds) {
    }

    public record RoleIdsRequest(@NotNull List<Long> roleIds) {
    }
}
