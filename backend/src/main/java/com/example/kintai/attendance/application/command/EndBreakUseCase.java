package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.AttendanceRecord;
import com.example.kintai.attendance.domain.repository.AttendanceRecordRepository;
import com.example.kintai.shared.kernel.contract.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 休憩終了ユースケース（UC-ATT-004）— 休憩中の従業員の休憩終了を記録する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>勤怠記録を取得する</li>
 *   <li>集約のendBreak()を呼び出す（ステータスはCLOCKED_INのまま、休憩時間を集約内で計算）</li>
 *   <li>集約・打刻エントリを保存し、ドメインイベントを一括で永続化・発行する</li>
 * </ol>
 * </p>
 */
@Service
@Transactional
public class EndBreakUseCase implements UseCase<EndBreakCommand, Void> {

    private static final Logger log = LoggerFactory.getLogger(EndBreakUseCase.class);

    /** 勤怠記録リポジトリ — 集約の保存用 */
    private final AttendanceRecordRepository attendanceRecordRepository;

    /** 共通サポート — レコード取得・イベント永続化用 */
    private final AttendanceUseCaseSupport support;

    public EndBreakUseCase(
            AttendanceRecordRepository attendanceRecordRepository,
            AttendanceUseCaseSupport support
    ) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.support = support;
    }

    /**
     * 休憩終了を実行する
     *
     * @param command 休憩終了コマンド（勤怠記録ID、打刻時刻、打刻元）
     * @return null（戻り値なし）
     */
    @Override
    public Void execute(EndBreakCommand command) {
        // 勤怠記録を取得する
        AttendanceRecord record = support.findRecordOrThrow(command.id());
        log.debug("休憩終了: attendanceId={}", command.id().value());

        // 集約の休憩終了コマンドを実行する（ステータス変化なし、休憩時間は集約内で計算）
        record.endBreak(command.clockTime(), command.source());

        // 集約をリポジトリに保存する（新規打刻エントリはsave内で自動保存される）
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // 集約に蓄積されたドメインイベントを一括で永続化・発行する
        support.publishAndPersistEvents(saved);

        log.debug("休憩終了完了: attendanceId={}", saved.getId().value());
        return null;
    }
}
