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

/**
 * リフレッシュトークンコントローラー — リフレッシュトークンによるトークンペア再発行を担当する
 *
 * <p>POST /api/v1/auth/refresh — リフレッシュトークンを検証し、
 * DBから最新の従業員情報を取得して新しいアクセストークン＋リフレッシュトークンを返す。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class RefreshController {

    private static final Logger log = LoggerFactory.getLogger(RefreshController.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final EmployeeAuthRepository employeeRepository;
    private final EmployeeRoleRepository roleRepository;

    public RefreshController(
            JwtTokenProvider jwtTokenProvider,
            EmployeeAuthRepository employeeRepository,
            EmployeeRoleRepository roleRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.employeeRepository = employeeRepository;
        this.roleRepository = roleRepository;
    }

    /** リフレッシュトークンリクエスト */
    record RefreshRequest(@NotBlank String refreshToken) {}

    /** トークンペアレスポンス */
    record AuthResponse(String accessToken, String refreshToken) {}

    /**
     * トークンリフレッシュ — リフレッシュトークンを検証して新しいトークンペアを発行する
     *
     * <p>リフレッシュ時にDBから最新の従業員情報を取得するため、
     * ロール変更などが即座に反映される。</p>
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest request) {
        // リフレッシュトークンの署名・有効期限・tokenTypeを検証する
        if (!jwtTokenProvider.validateRefreshToken(request.refreshToken())) {
            log.debug("リフレッシュトークン検証失敗");
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "リフレッシュトークンが無効または期限切れです");
            problem.setTitle("Unauthorized");
            problem.setType(URI.create("https://api.example.com/errors/unauthorized"));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
        }

        // リフレッシュトークンからメールアドレスを取得する
        String email = jwtTokenProvider.getEmailFromRefreshToken(request.refreshToken());

        // DBから最新の従業員情報を取得する（ロール変更を即反映）
        var employee = employeeRepository.findByEmailAndIsActiveTrue(email);
        if (employee.isEmpty()) {
            log.warn("リフレッシュ試行: 従業員が見つかりません email={}", email);
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "該当する従業員が見つかりません");
            problem.setTitle("Unauthorized");
            problem.setType(URI.create("https://api.example.com/errors/unauthorized"));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
        }

        var emp = employee.get();

        // 最新のロール一覧を取得する
        List<String> roles = roleRepository.findByEmployeeId(emp.getEmployeeId())
                .stream()
                .map(EmployeeRoleJpaEntity::getRole)
                .toList();

        // 認証済みユーザー情報を構築する
        AuthenticatedUser user = new AuthenticatedUser(
                emp.getEmail(),
                emp.getEmployeeId(),
                emp.getDepartmentId(),
                emp.getDepartmentName(),
                emp.getName(),
                roles
        );

        // 新しいアクセストークンとリフレッシュトークンを生成する
        String newAccessToken = jwtTokenProvider.generateToken(user);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(email);

        log.info("トークンリフレッシュ成功: employeeId={}", emp.getEmployeeId());

        return ResponseEntity.ok(new AuthResponse(newAccessToken, newRefreshToken));
    }
}
