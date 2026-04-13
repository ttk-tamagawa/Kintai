package com.example.kintai.attendance.presentation.controller;

import com.example.kintai.attendance.application.command.DeactivatePatternCommand;
import com.example.kintai.attendance.application.command.DeactivatePatternUseCase;
import com.example.kintai.attendance.application.command.DefinePatternCommand;
import com.example.kintai.attendance.application.command.DefinePatternUseCase;
import com.example.kintai.attendance.application.command.ReactivatePatternCommand;
import com.example.kintai.attendance.application.command.ReactivatePatternUseCase;
import com.example.kintai.attendance.application.query.GetPatternQuery;
import com.example.kintai.attendance.application.query.GetPatternQueryService;
import com.example.kintai.attendance.application.query.GetPatternsQuery;
import com.example.kintai.attendance.application.query.GetPatternsQueryService;
import com.example.kintai.attendance.domain.repository.ShiftQueryRepository.PatternSummary;
import com.example.kintai.attendance.presentation.dto.DefinePatternRequest;
import com.example.kintai.attendance.presentation.dto.PatternResponse;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * シフトパターンコントローラー — 5つのパターン管理APIエンドポイントを提供する
 *
 * <p>対応エンドポイント:
 * <ul>
 *   <li>POST /api/v1/shifts/patterns — パターン作成（5-6-1）</li>
 *   <li>GET /api/v1/shifts/patterns — パターン一覧（5-6-2）</li>
 *   <li>GET /api/v1/shifts/patterns/{patternId} — パターン詳細（5-6-3）</li>
 *   <li>POST /api/v1/shifts/patterns/{patternId}/actions/deactivate — 無効化（5-6-4）</li>
 *   <li>POST /api/v1/shifts/patterns/{patternId}/actions/reactivate — 再有効化（5-6-5）</li>
 * </ul>
 * </p>
 *
 * <p>コマンド系（作成・無効化・再有効化）はShiftPatternUseCaseに委譲し、
 * クエリ系（一覧・詳細）はShiftPatternQueryServiceに委譲する。
 * コマンド実行後のレスポンスはクエリサービスで最新状態を再取得して返却する。</p>
 *
 * <p>認可: POST/deactivate/reactivate→HR, GET→MANAGER/HR</p>
 */
@RestController
@RequestMapping("/api/v1/shifts/patterns")
public class ShiftPatternController {

    private static final Logger log = LoggerFactory.getLogger(ShiftPatternController.class);

    /** シフトパターン定義ユースケース（UC-SH-001） */
    private final DefinePatternUseCase definePatternUseCase;

    /** シフトパターン無効化ユースケース（UC-SH-002） */
    private final DeactivatePatternUseCase deactivatePatternUseCase;

    /** シフトパターン再有効化ユースケース（UC-SH-003） */
    private final ReactivatePatternUseCase reactivatePatternUseCase;

    /** シフトパターン一覧取得（UC-SH-Q01） */
    private final GetPatternsQueryService getPatternsQueryService;

    /** シフトパターン詳細取得（UC-SH-Q02） */
    private final GetPatternQueryService getPatternQueryService;

    public ShiftPatternController(
            DefinePatternUseCase definePatternUseCase,
            DeactivatePatternUseCase deactivatePatternUseCase,
            ReactivatePatternUseCase reactivatePatternUseCase,
            GetPatternsQueryService getPatternsQueryService,
            GetPatternQueryService getPatternQueryService
    ) {
        this.definePatternUseCase = definePatternUseCase;
        this.deactivatePatternUseCase = deactivatePatternUseCase;
        this.reactivatePatternUseCase = reactivatePatternUseCase;
        this.getPatternsQueryService = getPatternsQueryService;
        this.getPatternQueryService = getPatternQueryService;
    }

    // ========================================
    // POST / — パターン作成（5-6-1）
    // ========================================

    /**
     * 新しいシフトパターンを作成する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>リクエストDTOの値をコマンドサービスに渡してパターンを定義する</li>
     *   <li>作成されたパターンをクエリサービスで再取得してレスポンスを組み立てる</li>
     * </ol>
     * </p>
     *
     * @param request パターン定義リクエスト（name, startTime, endTime, breakMinutes, isOvernight）
     * @return 作成されたパターン情報（201 Created）
     */
    @PostMapping
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<PatternResponse> definePattern(
            @Valid @RequestBody DefinePatternRequest request) {

        log.debug("パターン作成リクエスト受信: name={}", request.name());

        // シフトパターン定義ユースケースを実行する（名前重複チェック、バリデーション含む）
        ShiftPatternId patternId = definePatternUseCase.execute(new DefinePatternCommand(
                request.name(),
                request.startTime(),
                request.endTime(),
                request.breakMinutes(),
                request.isOvernight()
        ));

        // 作成されたパターンをクエリサービスで再取得する
        PatternSummary pattern = getPatternQueryService.execute(new GetPatternQuery(patternId.value()));

        log.debug("パターン作成完了: patternId={}", patternId.value());
        return ResponseEntity.status(HttpStatus.CREATED).body(toPatternResponse(pattern));
    }

    // ========================================
    // GET / — パターン一覧（5-6-2）
    // ========================================

    /**
     * シフトパターン一覧を取得する
     *
     * <p>isActiveパラメータで有効/無効をフィルタできる。
     * パターン数は少数（通常50件以下）のため、ページネーションは不要。</p>
     *
     * @param isActive true=有効のみ, false=無効のみ, 未指定=全件
     * @return パターン一覧（パターン名昇順）
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'HR')")
    public ResponseEntity<List<PatternResponse>> getPatterns(
            @RequestParam(required = false) Boolean isActive) {

        log.debug("パターン一覧リクエスト受信: isActive={}", isActive);

        // クエリサービスでパターン一覧を取得する（名前昇順ソート済み）
        List<PatternSummary> patterns = getPatternsQueryService.execute(new GetPatternsQuery(isActive));

        // PatternSummary → PatternResponse に変換する
        List<PatternResponse> response = patterns.stream()
                .map(this::toPatternResponse)
                .toList();

        log.debug("パターン一覧取得完了: {}件", response.size());
        return ResponseEntity.ok(response);
    }

    // ========================================
    // GET /{patternId} — パターン詳細（5-6-3）
    // ========================================

    /**
     * シフトパターンの詳細を取得する
     *
     * @param patternId シフトパターンID（パスパラメータ）
     * @return パターン詳細
     */
    @GetMapping("/{patternId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'HR')")
    public ResponseEntity<PatternResponse> getPattern(@PathVariable UUID patternId) {
        log.debug("パターン詳細リクエスト受信: patternId={}", patternId);

        // クエリサービスでパターン詳細を取得する（見つからなければ404）
        PatternSummary pattern = getPatternQueryService.execute(new GetPatternQuery(patternId));

        log.debug("パターン詳細取得完了: name={}", pattern.name());
        return ResponseEntity.ok(toPatternResponse(pattern));
    }

    // ========================================
    // POST /{patternId}/actions/deactivate — 無効化（5-6-4）
    // ========================================

    /**
     * シフトパターンを無効化する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>コマンドサービスで無効化を実行する（未来割当チェック、ACTIVEガード含む）</li>
     *   <li>更新後のパターンをクエリサービスで再取得してレスポンスを組み立てる</li>
     * </ol>
     * </p>
     *
     * @param patternId シフトパターンID（パスパラメータ）
     * @return 無効化後のパターン情報（isActive=false）
     */
    @PostMapping("/{patternId}/actions/deactivate")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<PatternResponse> deactivatePattern(@PathVariable UUID patternId) {
        log.debug("パターン無効化リクエスト受信: patternId={}", patternId);

        // シフトパターン無効化ユースケースを実行する（未来割当チェック、ACTIVEガード含む）
        deactivatePatternUseCase.execute(new DeactivatePatternCommand(ShiftPatternId.of(patternId)));

        // 更新後のパターンをクエリサービスで再取得する
        PatternSummary pattern = getPatternQueryService.execute(new GetPatternQuery(patternId));

        log.debug("パターン無効化完了: patternId={}", patternId);
        return ResponseEntity.ok(toPatternResponse(pattern));
    }

    // ========================================
    // POST /{patternId}/actions/reactivate — 再有効化（5-6-5）
    // ========================================

    /**
     * シフトパターンを再有効化する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>コマンドサービスで再有効化を実行する（INACTIVEガード含む）</li>
     *   <li>更新後のパターンをクエリサービスで再取得してレスポンスを組み立てる</li>
     * </ol>
     * </p>
     *
     * @param patternId シフトパターンID（パスパラメータ）
     * @return 再有効化後のパターン情報（isActive=true）
     */
    @PostMapping("/{patternId}/actions/reactivate")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<PatternResponse> reactivatePattern(@PathVariable UUID patternId) {
        log.debug("パターン再有効化リクエスト受信: patternId={}", patternId);

        // シフトパターン再有効化ユースケースを実行する（INACTIVEガード含む）
        reactivatePatternUseCase.execute(new ReactivatePatternCommand(ShiftPatternId.of(patternId)));

        // 更新後のパターンをクエリサービスで再取得する
        PatternSummary pattern = getPatternQueryService.execute(new GetPatternQuery(patternId));

        log.debug("パターン再有効化完了: patternId={}", patternId);
        return ResponseEntity.ok(toPatternResponse(pattern));
    }

    // ========================================
    // DTO変換ヘルパー
    // ========================================

    /**
     * PatternSummary（Read Model）→ PatternResponse（API応答）に変換する
     *
     * @param summary クエリサービスから取得したパターン概要
     * @return APIレスポンスDTO
     */
    private PatternResponse toPatternResponse(PatternSummary summary) {
        return new PatternResponse(
                summary.id(),
                summary.name(),
                summary.startTime(),
                summary.endTime(),
                summary.breakMinutes(),
                summary.isOvernight(),
                summary.isActive(),
                summary.createdAt(),
                summary.updatedAt()
        );
    }
}
