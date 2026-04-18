package com.example.kintai.shared.infrastructure;

import com.example.kintai.shared.domain.exception.BusinessRuleViolationException;
import com.example.kintai.shared.domain.exception.ResourceNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * グローバル例外ハンドラー — RFC 7807形式の統一エラーレスポンスを返却する
 *
 * <p>全コントローラで発生する例外を一元的にキャッチし、
 * クライアントに統一フォーマット（ProblemDetail）でエラー情報を返す。
 * Spring Boot 4の組み込みRFC 7807サポート（ProblemDetail）を使用する。</p>
 *
 * <p>例外マッピング:
 * <ul>
 *   <li>MethodArgumentNotValidException → 400（Bean Validationエラー）</li>
 *   <li>HttpMessageNotReadableException → 400（JSON形式不正・enum値不正）</li>
 *   <li>IllegalArgumentException → 400（引数不正・enum値パース失敗）</li>
 *   <li>ResourceNotFoundException → 404（リソース未検出）</li>
 *   <li>BusinessRuleViolationException → 409（ドメインルール違反）</li>
 *   <li>IllegalStateException → 409（集約の状態遷移違反）</li>
 *   <li>AccessDeniedException → 403（アクセス拒否）</li>
 *   <li>AuthenticationException → 401（認証エラー、再スロー）</li>
 *   <li>Exception → 500（予期せぬエラー）</li>
 * </ul>
 * </p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Bean Validationエラー → 400 Bad Request
     *
     * <p>@NotNull, @Size 等のバリデーション違反時に発生する。
     * フィールド別のエラー詳細をerrorsプロパティに格納する。</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("バリデーションエラー: URI={}", request.getRequestURI());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "入力値にエラーがあります");
        problemDetail.setTitle("Validation Error");
        problemDetail.setType(URI.create("https://api.example.com/errors/validation"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));

        // フィールド別のエラー詳細を組み立てる
        List<Map<String, Object>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> Map.<String, Object>of(
                        "field", fieldError.getField(),
                        "message", fieldError.getDefaultMessage() != null
                                ? fieldError.getDefaultMessage() : "不正な値です",
                        "rejectedValue", fieldError.getRejectedValue() != null
                                ? fieldError.getRejectedValue() : "null"
                ))
                .toList();
        problemDetail.setProperty("errors", errors);

        return problemDetail;
    }

    /**
     * JSON形式不正・enum値不正 → 400 Bad Request
     *
     * <p>リクエストボディのJSON解析に失敗した場合や、
     * 無効なenum値が指定された場合に発生する。</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("リクエスト形式不正: URI={}, message={}", request.getRequestURI(), ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "リクエストの形式が不正です");
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("https://api.example.com/errors/validation"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));

        return problemDetail;
    }

    /**
     * 引数不正 → 400 Bad Request
     *
     * <p>値オブジェクトのバリデーションやenum値のパースに失敗した場合にスローされる
     * IllegalArgumentExceptionをキャッチする。</p>
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("引数不正: URI={}, message={}", request.getRequestURI(), ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("https://api.example.com/errors/bad-request"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));

        return problemDetail;
    }

    /**
     * リソース未検出 → 404 Not Found
     *
     * <p>リポジトリの検索で対象が見つからない場合にスローされる
     * ResourceNotFoundExceptionをキャッチする。</p>
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("リソース未検出: URI={}, message={}", request.getRequestURI(), ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Not Found");
        problemDetail.setType(URI.create("https://api.example.com/errors/not-found"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));

        return problemDetail;
    }

    /**
     * ドメインルール違反 → 409 Conflict
     *
     * <p>ドメインのビジネスルール（不変条件・事前条件）に違反した場合にスローされる
     * BusinessRuleViolationExceptionをキャッチする。
     * 例: 出勤済みの従業員に対する再出勤、無効化パターンの割当、パターン名の重複</p>
     */
    @ExceptionHandler(BusinessRuleViolationException.class)
    public ProblemDetail handleConflictException(
            BusinessRuleViolationException ex, HttpServletRequest request) {
        log.warn("ドメインルール違反: URI={}, message={}", request.getRequestURI(), ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("Conflict");
        problemDetail.setType(URI.create("https://api.example.com/errors/conflict"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));

        return problemDetail;
    }

    /**
     * 集約の状態遷移違反 → 409 Conflict
     *
     * <p>ドメイン集約のガード条件（状態遷移チェック）に違反した場合にスローされる
     * IllegalStateExceptionをキャッチする。
     * 例: 出勤済みの従業員に対する再出勤、休憩中でない従業員の休憩終了</p>
     */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleStateException(
            IllegalStateException ex, HttpServletRequest request) {
        log.warn("状態遷移違反: URI={}, message={}", request.getRequestURI(), ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("Conflict");
        problemDetail.setType(URI.create("https://api.example.com/errors/conflict"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));

        return problemDetail;
    }

    /**
     * アクセス拒否 → 403 Forbidden
     *
     * <p>@PreAuthorize 等のメソッドレベル認可で発生する AccessDeniedException を
     * キャッチして RFC 7807 形式で 403 を返す。
     * @ExceptionHandler(Exception.class) に先に捕捉されるのを防ぐ。</p>
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("アクセス拒否: URI={}", request.getRequestURI());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        problemDetail.setTitle("Forbidden");
        problemDetail.setType(URI.create("https://api.example.com/errors/forbidden"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));

        return problemDetail;
    }

    /**
     * 認証エラー → 401 Unauthorized
     *
     * <p>AuthenticationException を @ExceptionHandler(Exception.class) に
     * 先に捕捉されるのを防ぐため、明示的にキャッチして再スローする。
     * 実際の処理は JwtAuthenticationEntryPoint が担当する。</p>
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex) throws Exception {
        throw ex;
    }

    /**
     * 予期せぬエラー → 500 Internal Server Error
     *
     * <p>上記のいずれにも該当しない例外をキャッチするフォールバック。
     * エラー詳細はログに記録し、クライアントには汎用メッセージを返す。</p>
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneralException(
            Exception ex, HttpServletRequest request) {
        log.error("予期せぬエラー: URI={}", request.getRequestURI(), ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "予期せぬエラーが発生しました");
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(URI.create("https://api.example.com/errors/internal"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));

        return problemDetail;
    }
}
