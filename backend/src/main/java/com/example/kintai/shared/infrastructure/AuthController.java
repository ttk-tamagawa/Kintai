package com.example.kintai.shared.infrastructure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 認証コントローラー — Google OAuth によるログインとJWTトークン発行を担当する
 *
 * <p>POST /api/v1/auth/google — Google OAuth IDトークンによる認証</p>
 *
 * <p>開発用メールログイン（/dev-login）は {@link DevAuthController} に分離し、
 * dev/testプロファイル限定で提供する。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final EmployeeAuthRepository employeeRepository;
    private final EmployeeRoleRepository roleRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final GoogleTokenVerifier googleTokenVerifier;

    public AuthController(
            EmployeeAuthRepository employeeRepository,
            EmployeeRoleRepository roleRepository,
            JwtTokenProvider jwtTokenProvider,
            GoogleTokenVerifier googleTokenVerifier) {
        this.employeeRepository = employeeRepository;
        this.roleRepository = roleRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.googleTokenVerifier = googleTokenVerifier;
    }

    // --- リクエスト/レスポンス DTO ---

    /** Google OAuthログインリクエスト */
    record GoogleLoginRequest(@NotBlank String idToken) {}

    // --- エンドポイント ---

    /**
     * Google OAuth ログイン — Google IDトークンを検証してJWTを発行する
     */
    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        // Google IDトークンを検証してメールアドレスを取得する
        String email = googleTokenVerifier.verifyAndGetEmail(request.idToken());
        if (email == null) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "Google認証に失敗しました");
            problem.setTitle("Unauthorized");
            problem.setType(URI.create("https://api.example.com/errors/unauthorized"));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
        }

        log.info("Googleログイン試行: email={}", email);

        // メールアドレスで従業員を検索してJWTを発行する
        return authenticateByEmail(email);
    }

    // --- 共通ヘルパー ---

    /**
     * メールアドレスから従業員を検索し、JWTトークンを発行する
     */
    private ResponseEntity<?> authenticateByEmail(String email) {
        // 従業員マスタからメールで検索する
        var employee = employeeRepository.findByEmailAndIsActiveTrue(email);
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

        // 認証済みユーザー情報を作成する
        AuthenticatedUser user = new AuthenticatedUser(
                emp.getEmail(),
                emp.getEmployeeId(),
                emp.getDepartmentId(),
                emp.getDepartmentName(),
                emp.getName(),
                roles
        );

        // JWTトークンを生成して返却する
        String token = jwtTokenProvider.generateToken(user);
        log.info("ログイン成功: employeeId={}, roles={}", emp.getEmployeeId(), roles);

        return ResponseEntity.ok(Map.of("token", token));
    }
}
