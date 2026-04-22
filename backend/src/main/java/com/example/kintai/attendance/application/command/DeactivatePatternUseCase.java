package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.shift.ShiftPattern;
import com.example.kintai.attendance.domain.repository.ShiftPatternRepository;
import com.example.kintai.attendance.domain.repository.WeeklyScheduleRepository;
import com.example.kintai.shared.domain.exception.BusinessRuleViolationException;
import com.example.kintai.shared.domain.exception.ResourceNotFoundException;
import com.example.kintai.shared.kernel.contract.DomainEvent;
import com.example.kintai.shared.kernel.contract.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * シフトパターン無効化ユースケース（UC-SH-002）— シフトパターンを無効化する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>パターンをリポジトリから取得する（存在しなければ例外）</li>
 *   <li>未来の週次スケジュールで使用されていないことを検証する</li>
 *   <li>集約のdeactivate()を呼び出す（ACTIVEであることをガード）</li>
 *   <li>リポジトリに保存する</li>
 * </ol>
 * </p>
 */
@Service
@Transactional
public class DeactivatePatternUseCase implements UseCase<DeactivatePatternCommand, Void> {

    private static final Logger log = LoggerFactory.getLogger(DeactivatePatternUseCase.class);

    /** シフトパターンリポジトリ — パターンのCRUD操作 */
    private final ShiftPatternRepository shiftPatternRepository;

    /** 週次スケジュールリポジトリ — 未来割当チェック用 */
    private final WeeklyScheduleRepository weeklyScheduleRepository;

    /** Springイベント発行 — プロジェクターがRead Modelを更新するトリガー */
    private final ApplicationEventPublisher eventPublisher;

    public DeactivatePatternUseCase(
            ShiftPatternRepository shiftPatternRepository,
            WeeklyScheduleRepository weeklyScheduleRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.shiftPatternRepository = shiftPatternRepository;
        this.weeklyScheduleRepository = weeklyScheduleRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * シフトパターン無効化を実行する
     *
     * @param command シフトパターン無効化コマンド（パターンID）
     * @return null（戻り値なし）
     */
    @Override
    public Void execute(DeactivatePatternCommand command) {
        // パターンをリポジトリから取得する（存在しなければ例外）
        ShiftPattern pattern = findPatternOrThrow(command.patternId());
        log.debug("シフトパターン無効化: patternId={}, name={}",
                command.patternId().value(), pattern.getName().value());

        // 未来の週次スケジュールで使用されていないことを検証する
        if (weeklyScheduleRepository.existsFutureAssignmentByPatternId(command.patternId())) {
            throw new BusinessRuleViolationException(
                    "パターン「" + pattern.getName().value() + "」は未来の週次スケジュールで使用されているため無効化できません"
            );
        }

        // 集約のdeactivate()を呼び出す（ガード: ACTIVEであること、ドメインイベントを登録）
        pattern.deactivate();

        // リポジトリに保存する（楽観的ロックでバージョン管理）
        ShiftPattern saved = shiftPatternRepository.save(pattern);

        // 集約に蓄積されたドメインイベントを一括で発行する（Projector が Read Model を更新する）
        for (DomainEvent event : saved.getDomainEvents()) {
            eventPublisher.publishEvent(event);
        }
        saved.clearDomainEvents();

        log.debug("シフトパターン無効化完了: patternId={}", command.patternId().value());
        return null;
    }

    /**
     * シフトパターンをIDで取得する（見つからない場合は例外をスロー）
     */
    private ShiftPattern findPatternOrThrow(com.example.kintai.shared.domain.model.ShiftPatternId patternId) {
        return shiftPatternRepository.findById(patternId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "シフトパターンが見つかりません: " + patternId.value()));
    }
}
