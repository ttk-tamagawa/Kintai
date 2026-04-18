package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.shift.ShiftPattern;
import com.example.kintai.attendance.domain.model.shift.WeeklySchedule;
import com.example.kintai.attendance.domain.repository.ShiftPatternRepository;
import com.example.kintai.attendance.domain.repository.WeeklyScheduleRepository;
import com.example.kintai.shared.domain.exception.BusinessRuleViolationException;
import com.example.kintai.shared.domain.exception.ResourceNotFoundException;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.example.kintai.shared.kernel.contract.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;

/**
 * スケジュール割当ユースケース（UC-SH-004）— 新しい週次スケジュールを割り当てる
 *
 * <p>処理フロー:
 * <ol>
 *   <li>従業員ID+週開始日の重複をチェックする（1従業員1週1スケジュール）</li>
 *   <li>割当パターンが全てACTIVEであることを検証する（INV-SH-002）</li>
 *   <li>WeeklySchedule.assign()でドメインオブジェクトを作成する（DRAFT状態）</li>
 *   <li>リポジトリに保存する</li>
 *   <li>ASSIGNEDイベントをイベントストアに追記し、Springイベントを発行する</li>
 * </ol>
 * </p>
 */
@Service
@Transactional
public class AssignScheduleUseCase implements UseCase<AssignScheduleCommand, AssignScheduleResult> {

    private static final Logger log = LoggerFactory.getLogger(AssignScheduleUseCase.class);

    /** 週次スケジュールリポジトリ — スケジュールのCRUD操作 */
    private final WeeklyScheduleRepository weeklyScheduleRepository;

    /** シフトパターンリポジトリ — パターンのACTIVE検証用 */
    private final ShiftPatternRepository shiftPatternRepository;

    /** 共通サポート — イベント永続化・発行用 */
    private final WeeklyScheduleUseCaseSupport support;

    public AssignScheduleUseCase(
            WeeklyScheduleRepository weeklyScheduleRepository,
            ShiftPatternRepository shiftPatternRepository,
            WeeklyScheduleUseCaseSupport support
    ) {
        this.weeklyScheduleRepository = weeklyScheduleRepository;
        this.shiftPatternRepository = shiftPatternRepository;
        this.support = support;
    }

    /**
     * スケジュール割当を実行する
     *
     * @param command スケジュール割当コマンド（従業員ID、週開始日、曜日ごとの割当）
     * @return 割当結果（スケジュールとパターン名を含む）
     * @throws BusinessRuleViolationException 同一従業員・同一週にスケジュールが既に存在する場合
     */
    @Override
    public AssignScheduleResult execute(AssignScheduleCommand command) {
        log.debug("スケジュール割当: employeeId={}, weekStartDate={}",
                command.employeeId().value(), command.weekStartDate());

        // 従業員ID+週開始日の重複をチェックする（1従業員1週1スケジュール）
        if (weeklyScheduleRepository.existsByEmployeeIdAndWeekStartDate(
                command.employeeId(), command.weekStartDate())) {
            throw new BusinessRuleViolationException(
                    "この従業員の" + command.weekStartDate() + "週のスケジュールは既に登録されています"
            );
        }

        // 割当パターンが全てACTIVEであることを検証し、パターン名を取得する（INV-SH-002）
        Map<ShiftPatternId, String> patternNames = validateAndCollectPatternNames(command.assignments());

        // WeeklySchedule.assign()でドメインオブジェクトを作成する（DRAFT状態、月曜日チェック含む）
        WeeklySchedule schedule = WeeklySchedule.assign(
                command.employeeId(), command.weekStartDate(), command.assignments());

        // リポジトリに保存する（JPAが自動的にpersistを実行）
        WeeklySchedule saved = weeklyScheduleRepository.save(schedule);

        // 集約に蓄積されたドメインイベントを一括で永続化・発行する
        support.publishAndPersistEvents(saved);

        log.debug("スケジュール割当完了: scheduleId={}", saved.getId().value());
        return new AssignScheduleResult(saved, patternNames);
    }

    /**
     * 割当パターンが全てACTIVEであることを検証し、パターン名を収集する
     *
     * <p>INV-SH-002: 非アクティブなパターンは新規割当に使用不可。
     * 全パターンをリポジトリから取得し、1つでもINACTIVEがあれば例外をスローする。</p>
     *
     * @param assignments 曜日ごとのパターン割当
     * @return パターンID→パターン名のマップ
     * @throws ResourceNotFoundException パターンが見つからない場合
     * @throws BusinessRuleViolationException パターンがINACTIVEの場合
     */
    private Map<ShiftPatternId, String> validateAndCollectPatternNames(
            Map<DayOfWeek, ShiftPatternId> assignments) {
        Map<ShiftPatternId, String> patternNames = new HashMap<>();

        for (Map.Entry<DayOfWeek, ShiftPatternId> entry : assignments.entrySet()) {
            ShiftPatternId patternId = entry.getValue();

            // パターンをリポジトリから取得する（存在しなければ例外）
            ShiftPattern pattern = shiftPatternRepository.findById(patternId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "シフトパターンが見つかりません: " + patternId.value()
                                    + "（" + entry.getKey() + "の割当）"
                    ));

            // ACTIVEであることを検証する（INV-SH-002）
            if (!pattern.isActive()) {
                throw new BusinessRuleViolationException(
                        "パターン「" + pattern.getName().value() + "」は無効化されているため割当できません"
                                + "（" + entry.getKey() + "の割当）"
                );
            }

            // パターン名を収集する（レスポンス構築用）
            patternNames.put(patternId, pattern.getName().value());
        }

        return patternNames;
    }
}
