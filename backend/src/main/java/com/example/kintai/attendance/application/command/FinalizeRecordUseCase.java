package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.AttendanceRecord;
import com.example.kintai.attendance.domain.repository.AttendanceRecordRepository;
import com.example.kintai.shared.kernel.contract.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本締め確定ユースケース（UC-ATT-007）— 月次本締めにより勤怠記録を確定する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>勤怠記録を取得する</li>
 *   <li>集約のfinalizeRecord()を呼び出す（CLOCKED_OUT→FINALIZED）</li>
 *   <li>集約・イベントを保存し、イベントを発行する</li>
 * </ol>
 * ※ 打刻エントリの追加はなし（ステータス変更のみ）
 * </p>
 */
@Service
@Transactional
public class FinalizeRecordUseCase implements UseCase<FinalizeRecordCommand, Void> {

    private static final Logger log = LoggerFactory.getLogger(FinalizeRecordUseCase.class);

    /** 勤怠記録リポジトリ — 集約の保存用 */
    private final AttendanceRecordRepository attendanceRecordRepository;

    /** 共通サポート — レコード取得・イベント永続化用 */
    private final AttendanceUseCaseSupport support;

    public FinalizeRecordUseCase(
            AttendanceRecordRepository attendanceRecordRepository,
            AttendanceUseCaseSupport support
    ) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.support = support;
    }

    /**
     * 本締め確定を実行する
     *
     * @param command 本締め確定コマンド（勤怠記録ID、月次締めID）
     * @return null（戻り値なし）
     */
    @Override
    public Void execute(FinalizeRecordCommand command) {
        // 勤怠記録を取得する
        AttendanceRecord record = support.findRecordOrThrow(command.id());
        log.debug("本締め確定: attendanceId={}, monthlyClosingId={}",
                command.id().value(), command.monthlyClosingId());

        // 集約の本締め確定コマンドを実行する（CLOCKED_OUT→FINALIZED）
        record.finalizeRecord(command.monthlyClosingId());

        // 集約をリポジトリに保存する（打刻エントリの追加はなし）
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // 集約に蓄積されたドメインイベントを一括で永続化・発行する
        support.publishAndPersistEvents(saved);

        log.debug("本締め確定完了: attendanceId={}", saved.getId().value());
        return null;
    }
}
