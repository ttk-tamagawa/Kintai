package com.example.kintai.attendance.domain.repository;

import com.example.kintai.attendance.domain.model.AttendanceRecord;
import com.example.kintai.attendance.domain.model.WorkDate;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;

import java.util.Optional;

/**
 * 勤怠記録リポジトリ — 勤怠記録集約の永続化インターフェース
 *
 * <p>ドメイン層に定義し、インフラ層で実装する（依存性逆転の原則）。
 * 集約ルート（AttendanceRecord）単位でのCRUD操作を提供する。
 * 楽観的ロックによるバージョン管理を行い、同時更新を検知する。</p>
 */
public interface AttendanceRecordRepository {

    /**
     * 勤怠記録IDで検索する
     *
     * @param id 勤怠記録ID
     * @return 見つかった場合はAttendanceRecord、見つからない場合は空
     */
    Optional<AttendanceRecord> findById(AttendanceRecordId id);

    /**
     * 従業員IDと勤務日で検索する（1従業員1日1レコードの制約）
     *
     * @param employeeId 従業員ID
     * @param workDate   勤務日
     * @return 見つかった場合はAttendanceRecord、見つからない場合は空
     */
    Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(EmployeeId employeeId, WorkDate workDate);

    /**
     * 勤怠記録を保存する（新規作成または更新）
     *
     * <p>新規の場合はINSERT、既存の場合はUPDATE。
     * 楽観的ロックにより、バージョン不一致時は例外をスローする。
     * 保存後の最新バージョンを反映したレコードを返す。</p>
     *
     * @param record 保存する勤怠記録
     * @return バージョン更新済みの勤怠記録
     */
    AttendanceRecord save(AttendanceRecord record);

    /**
     * 従業員IDと勤務日の組み合わせが既に存在するか確認する
     *
     * @param employeeId 従業員ID
     * @param workDate   勤務日
     * @return 存在する場合true
     */
    boolean existsByEmployeeIdAndWorkDate(EmployeeId employeeId, WorkDate workDate);
}
