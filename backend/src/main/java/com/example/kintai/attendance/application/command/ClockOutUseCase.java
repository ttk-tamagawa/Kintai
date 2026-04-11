package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.AttendanceRecord;
import com.example.kintai.attendance.domain.model.shift.ShiftPattern;
import com.example.kintai.attendance.domain.repository.AttendanceRecordRepository;
import com.example.kintai.attendance.domain.repository.ShiftPatternRepository;
import com.example.kintai.attendance.domain.service.WorkDurationCalculator;
import com.example.kintai.shared.kernel.contract.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 退勤打刻ユースケース（UC-ATT-002）— 従業員の退勤を記録し勤務時間を計算する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>勤怠記録を取得する</li>
 *   <li>集約のclockOut()を呼び出す（CLOCKED_IN→CLOCKED_OUT）</li>
 *   <li>勤務時間を計算する（固定制 or シフト制）</li>
 *   <li>集約・打刻エントリ・イベントを保存し、2つのイベントを発行する</li>
 * </ol>
 * </p>
 */
@Service
@Transactional
public class ClockOutUseCase implements UseCase<ClockOutCommand, WorkDurationCalculator.CalculationResult> {

    private static final Logger log = LoggerFactory.getLogger(ClockOutUseCase.class);

    /** 勤怠記録リポジトリ — 集約の保存用 */
    private final AttendanceRecordRepository attendanceRecordRepository;

    /** 共通サポート — レコード取得・イベント永続化用 */
    private final AttendanceUseCaseSupport support;

    /** シフトパターンリポジトリ — シフト制の所定労働時間取得用 */
    private final ShiftPatternRepository shiftPatternRepository;

    /** 勤務時間計算サービス — 固定/シフト/フレックスの3パターン対応 */
    private final WorkDurationCalculator workDurationCalculator;

    public ClockOutUseCase(
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
     * 退勤打刻を実行する
     *
     * @param command 退勤打刻コマンド（勤怠記録ID、打刻時刻、打刻元）
     * @return 勤務時間+残業時間の計算結果
     */
    @Override
    public WorkDurationCalculator.CalculationResult execute(ClockOutCommand command) {
        // 勤怠記録を取得する
        AttendanceRecord record = support.findRecordOrThrow(command.id());
        log.debug("退勤打刻: attendanceId={}", command.id().value());

        // 集約の退勤打刻コマンドを実行する（CLOCKED_IN→CLOCKED_OUT）
        record.clockOut(command.clockTime(), command.source());

        // 勤務時間を計算する（シフトパターンの有無で計算方法を分岐）
        WorkDurationCalculator.CalculationResult calcResult = calculateWorkDuration(record);

        // 計算結果を集約に設定する
        record.calculateWorkDuration(calcResult.workDuration(), calcResult.overtimeDuration());

        // 集約をリポジトリに保存する（新規打刻エントリはsave内で自動保存される）
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // 集約に蓄積されたドメインイベントを一括で永続化・発行する
        support.publishAndPersistEvents(saved);

        log.debug("退勤打刻完了: attendanceId={}, netWorkMinutes={}",
                saved.getId().value(), calcResult.workDuration().netWorkMinutes());

        // 計算結果をコントローラに返す（レスポンスDTO組み立て用）
        return calcResult;
    }

    /**
     * 勤務時間を計算する（シフトパターンの有無で計算方法を分岐）
     *
     * <p>シフトパターンIDがある場合: ShiftPatternから所定労働時間を取得してシフト制で計算
     * <br>シフトパターンIDがない場合: 固定時間制（所定480分）で計算</p>
     *
     * @param record 勤怠記録（退勤打刻済みであること）
     * @return 勤務時間+残業時間の計算結果
     */
    private WorkDurationCalculator.CalculationResult calculateWorkDuration(AttendanceRecord record) {
        if (record.getShiftPatternId() != null) {
            // シフト制: シフトパターンから所定労働時間を取得して計算する
            ShiftPattern pattern = shiftPatternRepository.findById(record.getShiftPatternId())
                    .orElseThrow(() -> new IllegalStateException(
                            "シフトパターンが見つかりません: " + record.getShiftPatternId().value()));
            int scheduledMinutes = pattern.calculateScheduledMinutes();
            return workDurationCalculator.calculateForShift(record, scheduledMinutes);
        } else {
            // 固定時間制: 所定労働時間480分（8時間）で計算する
            return workDurationCalculator.calculateForFixed(record);
        }
    }
}
