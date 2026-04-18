package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.AttendanceRecord;
import com.example.kintai.attendance.domain.model.AttendanceStatus;
import com.example.kintai.attendance.domain.model.shift.ShiftPattern;
import com.example.kintai.attendance.domain.repository.AttendanceRecordRepository;
import com.example.kintai.attendance.domain.repository.ShiftPatternRepository;
import com.example.kintai.attendance.domain.service.WorkDurationCalculator;
import com.example.kintai.shared.domain.exception.ResourceNotFoundException;
import com.example.kintai.shared.kernel.contract.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 打刻修正ユースケース（UC-ATT-005）— 承認済みの打刻修正を適用する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>勤怠記録を取得する</li>
 *   <li>集約のcorrectClock()を呼び出す（source=CORRECTIONの新エントリ追加、修正前時刻は集約内で取得）</li>
 *   <li>退勤済みの場合は勤務時間を再計算する</li>
 *   <li>集約・打刻エントリを保存し、ドメインイベントを一括で永続化・発行する</li>
 * </ol>
 * </p>
 */
@Service
@Transactional
public class CorrectClockUseCase implements UseCase<CorrectClockCommand, Void> {

    private static final Logger log = LoggerFactory.getLogger(CorrectClockUseCase.class);

    /** 勤怠記録リポジトリ — 集約の保存用 */
    private final AttendanceRecordRepository attendanceRecordRepository;

    /** 共通サポート — レコード取得・イベント永続化用 */
    private final AttendanceUseCaseSupport support;

    /** シフトパターンリポジトリ — シフト制の所定労働時間取得用 */
    private final ShiftPatternRepository shiftPatternRepository;

    /** 勤務時間計算サービス — 固定/シフト/フレックスの3パターン対応 */
    private final WorkDurationCalculator workDurationCalculator;

    public CorrectClockUseCase(
            AttendanceRecordRepository attendanceRecordRepository,
            AttendanceUseCaseSupport support,
            ShiftPatternRepository shiftPatternRepository,
            WorkDurationCalculator workDurationCalculator
    ) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.support = support;
        this.shiftPatternRepository = shiftPatternRepository;
        this.workDurationCalculator = workDurationCalculator;
    }

    /**
     * 打刻修正を実行する
     *
     * @param command 打刻修正コマンド（勤怠記録ID、打刻修正内容）
     * @return null（戻り値なし）
     */
    @Override
    public Void execute(CorrectClockCommand command) {
        // 勤怠記録を取得する
        AttendanceRecord record = support.findRecordOrThrow(command.id());
        log.debug("打刻修正: attendanceId={}, targetType={}",
                command.id().value(), command.correction().targetType());

        // 集約の打刻修正コマンドを実行する（source=CORRECTIONの新エントリが追加される、修正前時刻は集約内で取得）
        record.correctClock(command.correction());

        // 退勤済みの場合は勤務時間を再計算する（出勤・退勤の両方が揃っている）
        if (record.getStatus() == AttendanceStatus.CLOCKED_OUT) {
            WorkDurationCalculator.CalculationResult calcResult = calculateWorkDuration(record);
            record.calculateWorkDuration(calcResult.workDuration(), calcResult.overtimeDuration());
        }

        // 集約をリポジトリに保存する（新規打刻エントリはsave内で自動保存される）
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // 集約に蓄積されたドメインイベントを一括で永続化・発行する
        support.publishAndPersistEvents(saved);

        log.debug("打刻修正完了: attendanceId={}", saved.getId().value());
        return null;
    }

    /**
     * 勤務時間を計算する（シフトパターンの有無で計算方法を分岐）
     *
     * @param record 勤怠記録（退勤打刻済みであること）
     * @return 勤務時間+残業時間の計算結果
     */
    private WorkDurationCalculator.CalculationResult calculateWorkDuration(AttendanceRecord record) {
        if (record.getShiftPatternId() != null) {
            // シフト制: シフトパターンから所定労働時間を取得して計算する
            ShiftPattern pattern = shiftPatternRepository.findById(record.getShiftPatternId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "シフトパターンが見つかりません: " + record.getShiftPatternId().value()));
            int scheduledMinutes = pattern.calculateScheduledMinutes();
            return workDurationCalculator.calculateForShift(record, scheduledMinutes);
        } else {
            // 固定時間制: 所定労働時間480分（8時間）で計算する
            return workDurationCalculator.calculateForFixed(record);
        }
    }
}
