package com.example.kintai.shared.infrastructure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 開発用認証コントローラー — 開発・テスト環境限定のメールログイン
 *
 * <p>{@code @Profile({"dev", "test"})} により、devまたはtestプロファイル時のみ
 * Beanが生成される。本番環境（prodプロファイル）ではこのコントローラー自体が
 * 存在しないため、dev-loginエンドポイントにアクセスすることは不可能。</p>
 */
@Profile({"dev", "test"})
@RestController
@RequestMapping("/api/v1/auth")
public class DevAuthController {

    private static final Logger log = LoggerFactory.getLogger(DevAuthController.class);

    private final EmployeeAuthRepository employeeRepository;
    private final EmployeeRoleRepository roleRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public DevAuthController(
            EmployeeAuthRepository employeeRepository,
            EmployeeRoleRepository roleRepository,
            JwtTokenProvider jwtTokenProvider) {
        this.employeeRepository = employeeRepository;
        this.roleRepository = roleRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /** 開発用ログインリクエスト */
    record DevLoginRequest(@NotBlank @Email String email) {}

    /**
     * 開発用メールログイン — email だけでJWTを発行する
     *
     * <p>{@code @Profile({"dev", "test"})} により開発・テスト環境でのみ有効。
     * 本番環境ではBean自体が存在しないため、このエンドポイントは利用不可。</p>
     */
    @PostMapping("/dev-login")
    public ResponseEntity<?> devLogin(@Valid @RequestBody DevLoginRequest request) {
        log.info("開発ログイン試行: email={}", request.email());

        // メールアドレスで従業員を検索する
        var employee = employeeRepository.findByEmailAndIsActiveTrue(request.email());
        if (employee.isEmpty()) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "該当する従業員が見つかりません");
            problem.setTitle("Unauthorized");
            problem.setType(URI.create("https://api.example.com/errors/unauthorized"));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
        }

        var emp = employee.get();

        // 従業員に紐づくロール一覧を取得する
        List<String> roles = roleRepository.findByEmployeeId(emp.getEmployeeId())
                .stream()
                .map(EmployeeRoleJpaEntity::getRole)
                .toList();

        // 認証済みユーザー情報を作成してJWTトークンを生成する
        AuthenticatedUser user = new AuthenticatedUser(
                emp.getEmail(),
                emp.getEmployeeId(),
                emp.getDepartmentId(),
                emp.getDepartmentName(),
                emp.getName(),
                roles
        );

        String token = jwtTokenProvider.generateToken(user);
        log.info("開発ログイン成功: employeeId={}, roles={}", emp.getEmployeeId(), roles);

        return ResponseEntity.ok(Map.of("token", token));
    }
}
