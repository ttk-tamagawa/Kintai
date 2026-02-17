package com.example.kintai.attendance.domain.repository;

import com.example.kintai.attendance.domain.model.ClockEntry;
import com.example.kintai.shared.domain.model.AttendanceRecordId;

import java.util.List;

/**
 * 打刻エントリリポジトリ — 打刻ログの追記専用リポジトリ（INSERT ONLY）
 *
 * <p>一度記録された打刻は変更・削除しない。
 * 打刻修正の場合はsource=CORRECTIONの新しいエントリが追加される。
 * 読み取り用のfindメソッドも提供し、集約復元時に利用する。</p>
 */
public interface ClockEntryRepository {

    /**
     * 打刻エントリを一括追記する
     *
     * @param attendanceId 対象の勤怠記録ID
     * @param entries      追加する打刻エントリのリスト
     */
    void appendAll(AttendanceRecordId attendanceId, List<ClockEntry> entries);

    /**
     * 勤怠記録IDに紐づく全打刻エントリを取得する（時刻昇順）
     *
     * @param attendanceId 勤怠記録ID
     * @return 打刻エントリのリスト（時刻昇順）
     */
    List<ClockEntry> findByAttendanceId(AttendanceRecordId attendanceId);
}
