package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.AttendanceRecord;
import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.attendance.domain.model.WorkDate;
import com.example.kintai.attendance.domain.repository.AttendanceRecordRepository;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.kernel.contract.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;

/**
 * 出勤打刻ユースケース（UC-ATT-001）— 従業員の出勤を記録する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>打刻時刻からAsia/Tokyoタイムゾーンで勤務日を算出する</li>
 *   <li>従業員ID+勤務日で既存レコードを検索し、なければ新規作成する</li>
 *   <li>集約のclockIn()を呼び出す（NOT_CLOCKED→CLOCKED_IN）</li>
 *   <li>集約・打刻エントリ・イベントを保存し、イベントを発行する</li>
 * </ol>
 * </p>
 */
@Service
@Transactional
public class ClockInUseCase implements UseCase<ClockInCommand, AttendanceRecordId> {

    private static final Logger log = LoggerFactory.getLogger(ClockInUseCase.class);

    /** タイムゾーン: Asia/Tokyo（打刻時刻から勤務日を算出するために使用） */
    private static final ZoneId ZONE_TOKYO = ZoneId.of("Asia/Tokyo");

    /** 勤怠記録リポジトリ — 集約のCRUD操作 */
    private final AttendanceRecordRepository attendanceRecordRepository;

    /** 共通サポート — イベント永続化用 */
    private final AttendanceUseCaseSupport support;

    public ClockInUseCase(
            AttendanceRecordRepository attendanceRecordRepository,
            AttendanceUseCaseSupport support
    ) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.support = support;
    }

    /**
     * 出勤打刻を実行する
     *
     * @param command 出勤打刻コマンド（従業員ID、打刻時刻、打刻元、シフトパターンID）
     * @return 作成または更新された勤怠記録ID
     */
    @Override
    public AttendanceRecordId execute(ClockInCommand command) {
        // 打刻時刻からAsia/Tokyoタイムゾーンで勤務日を算出する
        WorkDate workDate = deriveWorkDate(command.clockTime());
        log.debug("出勤打刻: employeeId={}, workDate={}",
                command.employeeId().value(), workDate.value());

        // 既存の勤怠記録を検索し、なければ新規作成する
        AttendanceRecord record = attendanceRecordRepository
                .findByEmployeeIdAndWorkDate(command.employeeId(), workDate)
                .orElseGet(() -> AttendanceRecord.create(
                        command.employeeId(), workDate, command.shiftPatternId()));

        // 集約の出勤打刻コマンドを実行する（NOT_CLOCKED→CLOCKED_IN）
        record.clockIn(command.clockTime(), command.source());

        // 集約をリポジトリに保存する（新規打刻エントリはsave内で自動保存される）
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // 集約に蓄積されたドメインイベントを一括で永続化・発行する
        support.publishAndPersistEvents(saved);

        log.debug("出勤打刻完了: attendanceId={}", saved.getId().value());
        return saved.getId();
    }

    /**
     * 打刻時刻からAsia/Tokyoタイムゾーンで勤務日を算出する
     *
     * <p>Instant（UTC瞬間値）をAsia/Tokyoのローカル日付に変換し、
     * WorkDate値オブジェクトとして返す。</p>
     *
     * @param clockTime 打刻時刻
     * @return 勤務日
     */
    private WorkDate deriveWorkDate(ClockTime clockTime) {
        return new WorkDate(clockTime.value().atZone(ZONE_TOKYO).toLocalDate());
    }
}
