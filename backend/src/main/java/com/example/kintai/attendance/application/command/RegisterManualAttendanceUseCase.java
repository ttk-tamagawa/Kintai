package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.AttendanceRecord;
import com.example.kintai.attendance.domain.model.shift.ShiftPattern;
import com.example.kintai.attendance.domain.repository.AttendanceRecordRepository;
import com.example.kintai.attendance.domain.repository.ShiftPatternRepository;
import com.example.kintai.attendance.domain.service.WorkDurationCalculator;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.kernel.contract.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 勤務実績登録ユースケース（UC-ATT-006）— 承認済みの手動勤務実績を登録する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>既存レコードを検索し、なければ新規作成する</li>
 *   <li>集約のregisterManualAttendance()を呼び出す（NOT_CLOCKED→CLOCKED_OUT）</li>
 *   <li>勤務時間を計算する（固定制 or シフト制）</li>
 *   <li>集約・打刻エントリ・イベントを保存し、2つのイベントを発行する</li>
 * </ol>
 * </p>
 */
@Service
@Transactional
public class RegisterManualAttendanceUseCase implements UseCase<RegisterManualAttendanceCommand, AttendanceRecordId> {

    private static final Logger log = LoggerFactory.getLogger(RegisterManualAttendanceUseCase.class);

    /** 勤怠記録リポジトリ — 集約のCRUD操作 */
    private final AttendanceRecordRepository attendanceRecordRepository;

    /** 共通サポート — イベント永続化用 */
    private final AttendanceUseCaseSupport support;

    /** シフトパターンリポジトリ — シフト制の所定労働時間取得用 */
    private final ShiftPatternRepository shiftPatternRepository;

    /** 勤務時間計算サービス — 固定/シフト/フレックスの3パターン対応 */
    private final WorkDurationCalculator workDurationCalculator;

    public RegisterManualAttendanceUseCase(
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
     * 手動勤務実績登録を実行する
     *
     * @param command 手動勤務登録コマンド（従業員ID、勤務日、手動勤務内容、シフトパターンID）
     * @return 作成された勤怠記録ID
     */
    @Override
    public AttendanceRecordId execute(RegisterManualAttendanceCommand command) {
        log.debug("手動勤務登録: employeeId={}, workDate={}",
                command.employeeId().value(), command.workDate().value());

        // 既存レコードを検索し、なければ新規作成する
        AttendanceRecord record = attendanceRecordRepository
                .findByEmployeeIdAndWorkDate(command.employeeId(), command.workDate())
                .orElseGet(() -> AttendanceRecord.create(
                        command.employeeId(), command.workDate(), command.shiftPatternId()));

        // 集約の手動勤務登録コマンドを実行する（出勤+退勤を一括登録、NOT_CLOCKED→CLOCKED_OUT）
        record.registerManualAttendance(command.manual());

        // 勤務時間を計算する（シフトパターンの有無で計算方法を分岐）
        WorkDurationCalculator.CalculationResult calcResult = calculateWorkDuration(record);

        // 計算結果を集約に設定する
        record.calculateWorkDuration(calcResult.workDuration(), calcResult.overtimeDuration());

        // 集約をリポジトリに保存する（新規打刻エントリはsave内で自動保存される）
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // 集約に蓄積されたドメインイベントを一括で永続化・発行する
        support.publishAndPersistEvents(saved);

        log.debug("手動勤務登録完了: attendanceId={}, netWorkMinutes={}",
                saved.getId().value(), calcResult.workDuration().netWorkMinutes());
        return saved.getId();
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
