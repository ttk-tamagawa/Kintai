package com.example.kintai.attendance.application.query;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.kintai.attendance.domain.repository.ShiftQueryRepository;
import com.example.kintai.attendance.domain.repository.ShiftQueryRepository.PatternSummary;

/**
 * シフトパターンクエリサービス — シフトパターンの2つの読み取りユースケースを統合するアプリケーションサービス
 *
 * <p>CQRS（コマンドクエリ責務分離）の読み取り側。
 * ShiftQueryRepository（Read Model参照用）を使用してシフトパターンのデータを取得する。
 * Read Modelはshift_patternsテーブルから直接読み取る（パターンは単純構造のため）。</p>
 *
 * <p>対応ユースケース:
 * <ul>
 *   <li>UC-SH-Q01: シフトパターン一覧を取得する（getPatterns）</li>
 *   <li>UC-SH-Q02: シフトパターン詳細を取得する（getPattern）</li>
 * </ul>
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class ShiftPatternQueryService {

    private static final Logger log = LoggerFactory.getLogger(ShiftPatternQueryService.class);

    /** シフトクエリリポジトリ — Read Model参照用 */
    private final ShiftQueryRepository shiftQueryRepository;

    /**
     * コンストラクタ — クエリリポジトリを注入する
     */
    public ShiftPatternQueryService(ShiftQueryRepository shiftQueryRepository) {
        this.shiftQueryRepository = shiftQueryRepository;
    }

    // ========================================
    // UC-SH-Q01: シフトパターン一覧を取得する
    // ========================================

    /**
     * シフトパターン一覧を取得する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>isActiveフィルタに基づいてパターン一覧を取得する</li>
     *   <li>パターン名の昇順でソート済みのリストを返す</li>
     * </ol>
     * パターン数は少数（通常50件以下）のため、DBレベルでページネーションは不要。
     * API層で必要に応じてインメモリページネーションを適用する。</p>
     *
     * @param activeOnly trueの場合、有効（ACTIVE）なパターンのみ取得する。
     *                   falseの場合、全パターン（ACTIVE + INACTIVE）を取得する。
     * @return パターン概要のリスト（パターン名昇順）
     */
    public List<PatternSummary> getPatterns(boolean activeOnly) {
        log.debug("シフトパターン一覧取得: activeOnly={}", activeOnly);

        // ShiftQueryRepositoryから有効/全パターンを取得する（名前昇順ソート済み）
        List<PatternSummary> patterns = shiftQueryRepository.findPatterns(activeOnly);

        log.debug("シフトパターン一覧取得完了: {}件", patterns.size());
        return patterns;
    }

    // ========================================
    // UC-SH-Q02: シフトパターン詳細を取得する
    // ========================================

    /**
     * シフトパターンの詳細を取得する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>パターンIDでShiftQueryRepositoryから検索する</li>
     *   <li>見つからない場合は例外をスローする</li>
     * </ol>
     * </p>
     *
     * @param patternId シフトパターンID
     * @return パターン概要
     * @throws IllegalArgumentException パターンが見つからない場合
     */
    public PatternSummary getPattern(UUID patternId) {
        log.debug("シフトパターン詳細取得: patternId={}", patternId);

        // ShiftQueryRepositoryからIDで検索する（見つからなければ例外）
        PatternSummary pattern = shiftQueryRepository.findPatternById(patternId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "シフトパターンが見つかりません: " + patternId));

        log.debug("シフトパターン詳細取得完了: name={}", pattern.name());
        return pattern;
    }
}
