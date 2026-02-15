package com.example.kintai.attendance.domain.model;

import com.example.kintai.shared.domain.model.ApprovalId;
import java.util.Objects;

/**
 * 手動勤務VO — 承認済みの手動勤務登録内容を表す値オブジェクト
 *
 * <p>打刻漏れ等で出退勤記録がない場合に、上長承認済みの手動登録申請に基づき
 * システムが自動的に勤務実績を登録する際に使用する。
 * 勤務開始時刻、終了時刻、勤務種別、申請理由、承認IDを保持する。</p>
 *
 * @param startTime  勤務開始時刻（null不可）
 * @param endTime    勤務終了時刻（null不可）
 * @param type       勤務種別（null不可）— 例: "通常勤務", "出張" 等
 * @param reason     登録理由（null不可）— 例: "打刻漏れのため"
 * @param approvalId 承認ID（null不可）
 */
public record ManualAttendance(
        ClockTime startTime,
        ClockTime endTime,
        String type,
        String reason,
        ApprovalId approvalId
) {

    /**
     * コンパクトコンストラクタ — 全フィールドのnullチェックを行う
     */
    public ManualAttendance {
        Objects.requireNonNull(startTime, "勤務開始時刻はnullにできません");
        Objects.requireNonNull(endTime, "勤務終了時刻はnullにできません");
        Objects.requireNonNull(type, "勤務種別はnullにできません");
        Objects.requireNonNull(reason, "登録理由はnullにできません");
        Objects.requireNonNull(approvalId, "承認IDはnullにできません");
    }
}
