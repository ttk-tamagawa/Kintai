package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.shift.PatternName;
import com.example.kintai.attendance.domain.model.shift.ShiftPattern;
import com.example.kintai.attendance.domain.model.shift.event.ShiftPatternDefinedEvent;
import com.example.kintai.attendance.domain.repository.ShiftPatternRepository;
import com.example.kintai.attendance.domain.repository.WeeklyScheduleRepository;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

/**
 * シフトパターンコマンドサービス — シフトパターンの3つの書き込みユースケースを統合するアプリケーションサービス
 *
 * <p>各コマンドメソッドは以下の共通フローで処理する:
 * <ol>
 *   <li>ガード条件を検証する（名前重複チェック、未来割当チェック等）</li>
 *   <li>ドメインオブジェクトのファクトリ/コマンドメソッドを呼び出す</li>
 *   <li>リポジトリに保存する</li>
 *   <li>必要に応じてドメインイベントを発行する</li>
 * </ol>
 * </p>
 *
 * <p>ShiftPatternはイベントソーシングを使用しない（状態遷移が単純なため）。
 * ただしdefinePattern時にはShiftPatternDefinedEventをSpringイベントとして発行し、
 * プロジェクターがRead Modelのパターン名マスタを更新する。</p>
 *
 * <p>対応ユースケース:
 * <ul>
 *   <li>UC-SH-001: シフトパターンを定義する（definePattern）</li>
 *   <li>UC-SH-002: シフトパターンを無効化する（deactivatePattern）</li>
 *   <li>UC-SH-003: シフトパターンを再有効化する（reactivatePattern）</li>
 * </ul>
 * </p>
 */
@Service
@Transactional
public class ShiftPatternCommandService {

    private static final Logger log = LoggerFactory.getLogger(ShiftPatternCommandService.class);

    /** シフトパターンリポジトリ — パターンのCRUD操作 */
    private final ShiftPatternRepository shiftPatternRepository;

    /** 週次スケジュールリポジトリ — 未来割当チェック用 */
    private final WeeklyScheduleRepository weeklyScheduleRepository;

    /** Springイベント発行 — プロジェクターがRead Modelを更新するトリガー */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * コンストラクタ — 3つの依存を注入する
     */
    public ShiftPatternCommandService(
            ShiftPatternRepository shiftPatternRepository,
            WeeklyScheduleRepository weeklyScheduleRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.shiftPatternRepository = shiftPatternRepository;
        this.weeklyScheduleRepository = weeklyScheduleRepository;
        this.eventPublisher = eventPublisher;
    }

    // ========================================
    // UC-SH-001: シフトパターンを定義する
    // ========================================

    /**
     * 新しいシフトパターンを定義する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>パターン名の値オブジェクトを生成する（2〜20文字バリデーション）</li>
     *   <li>パターン名の一意性をリポジトリで検証する（INV-SH-001）</li>
     *   <li>ShiftPattern.define()でドメインオブジェクトを生成する（初期状態: ACTIVE）</li>
     *   <li>リポジトリに保存する</li>
     *   <li>ShiftPatternDefinedEventを発行する</li>
     * </ol>
     * </p>
     *
     * @param name         パターン名（早番、遅番等。2〜20文字）
     * @param startTime    勤務開始時刻
     * @param endTime      勤務終了時刻
     * @param breakMinutes 休憩時間（分。0〜120）
     * @param isOvernight  夜勤フラグ（trueなら日跨ぎパターン）
     * @return 作成されたシフトパターンID
     * @throws IllegalArgumentException パターン名が重複している場合
     */
    public ShiftPatternId definePattern(
            String name, LocalTime startTime, LocalTime endTime,
            int breakMinutes, boolean isOvernight
    ) {
        // パターン名の値オブジェクトを生成する（2〜20文字のバリデーションが実行される）
        PatternName patternName = new PatternName(name);
        log.debug("シフトパターン定義: name={}", patternName.value());

        // パターン名の一意性をリポジトリで検証する（INV-SH-001: 名前はシステム全体で一意）
        if (shiftPatternRepository.existsByName(patternName)) {
            throw new IllegalArgumentException(
                    "パターン名「" + patternName.value() + "」は既に使用されています"
            );
        }

        // ShiftPattern.define()でドメインオブジェクトを生成する（初期状態: ACTIVE、version=0）
        ShiftPattern pattern = ShiftPattern.define(
                patternName, startTime, endTime, breakMinutes, isOvernight
        );

        // リポジトリに保存する（JPAが自動的にpersistを実行）
        ShiftPattern saved = shiftPatternRepository.save(pattern);

        // ShiftPatternDefinedEventを生成して発行する（プロジェクターがRead Modelを更新）
        ShiftPatternDefinedEvent event = ShiftPatternDefinedEvent.of(
                saved.getId(), saved.getName(),
                saved.getStartTime(), saved.getEndTime(), saved.isOvernight()
        );
        eventPublisher.publishEvent(event);

        log.debug("シフトパターン定義完了: patternId={}", saved.getId().value());
        return saved.getId();
    }

    // ========================================
    // UC-SH-002: シフトパターンを無効化する
    // ========================================

    /**
     * シフトパターンを無効化する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>パターンをリポジトリから取得する（存在しなければ例外）</li>
     *   <li>未来の週次スケジュールで使用されていないことを検証する</li>
     *   <li>集約のdeactivate()を呼び出す（ACTIVEであることをガード）</li>
     *   <li>リポジトリに保存する</li>
     * </ol>
     * </p>
     *
     * @param patternId 無効化するシフトパターンID
     * @throws IllegalArgumentException パターンが見つからない場合
     * @throws IllegalStateException    既にINACTIVEの場合、または未来の割当で使用中の場合
     */
    public void deactivatePattern(ShiftPatternId patternId) {
        // パターンをリポジトリから取得する（存在しなければ例外）
        ShiftPattern pattern = findPatternOrThrow(patternId);
        log.debug("シフトパターン無効化: patternId={}, name={}", patternId.value(), pattern.getName().value());

        // 未来の週次スケジュールで使用されていないことを検証する
        if (weeklyScheduleRepository.existsFutureAssignmentByPatternId(patternId)) {
            throw new IllegalStateException(
                    "パターン「" + pattern.getName().value() + "」は未来の週次スケジュールで使用されているため無効化できません"
            );
        }

        // 集約のdeactivate()を呼び出す（ガード: ACTIVEであること）
        pattern.deactivate();

        // リポジトリに保存する（楽観的ロックでバージョン管理）
        shiftPatternRepository.save(pattern);

        log.debug("シフトパターン無効化完了: patternId={}", patternId.value());
    }

    // ========================================
    // UC-SH-003: シフトパターンを再有効化する
    // ========================================

    /**
     * シフトパターンを再有効化する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>パターンをリポジトリから取得する（存在しなければ例外）</li>
     *   <li>集約のreactivate()を呼び出す（INACTIVEであることをガード）</li>
     *   <li>リポジトリに保存する</li>
     * </ol>
     * </p>
     *
     * @param patternId 再有効化するシフトパターンID
     * @throws IllegalArgumentException パターンが見つからない場合
     * @throws IllegalStateException    既にACTIVEの場合
     */
    public void reactivatePattern(ShiftPatternId patternId) {
        // パターンをリポジトリから取得する（存在しなければ例外）
        ShiftPattern pattern = findPatternOrThrow(patternId);
        log.debug("シフトパターン再有効化: patternId={}, name={}", patternId.value(), pattern.getName().value());

        // 集約のreactivate()を呼び出す（ガード: INACTIVEであること）
        pattern.reactivate();

        // リポジトリに保存する（楽観的ロックでバージョン管理）
        shiftPatternRepository.save(pattern);

        log.debug("シフトパターン再有効化完了: patternId={}", patternId.value());
    }

    // ========================================
    // ヘルパーメソッド
    // ========================================

    /**
     * シフトパターンをIDで取得する（見つからない場合は例外をスロー）
     *
     * @param patternId シフトパターンID
     * @return シフトパターン
     * @throws IllegalArgumentException パターンが見つからない場合
     */
    private ShiftPattern findPatternOrThrow(ShiftPatternId patternId) {
        return shiftPatternRepository.findById(patternId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "シフトパターンが見つかりません: " + patternId.value()));
    }
}
