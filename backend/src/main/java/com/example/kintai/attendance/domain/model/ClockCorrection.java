package com.example.kintai.attendance.domain.model;

import com.example.kintai.shared.domain.model.ApprovalId;
import java.util.Objects;

/**
 * 打刻修正VO — 承認済みの打刻修正内容を表す値オブジェクト
 *
 * <p>上長承認済みの打刻修正申請に基づき、システムが自動的に打刻を修正する際に使用する。
 * 修正対象の打刻種別、修正後の時刻、修正理由、承認IDを保持する。
 * 元の打刻は保持されたまま、source=CORRECTIONの新しいClockEntryが追加される。</p>
 *
 * @param targetType    修正対象の打刻種別（null不可）
 * @param correctedTime 修正後の打刻時刻（null不可）
 * @param reason        修正理由（null不可）
 * @param approvalId    承認ID（null不可）
 */
public record ClockCorrection(
        ClockType targetType,
        ClockTime correctedTime,
        String reason,
        ApprovalId approvalId
) {

    /**
     * コンパクトコンストラクタ — 全フィールドのnullチェックを行う
     */
    public ClockCorrection {
        Objects.requireNonNull(targetType, "修正対象の打刻種別はnullにできません");
        Objects.requireNonNull(correctedTime, "修正後の打刻時刻はnullにできません");
        Objects.requireNonNull(reason, "修正理由はnullにできません");
        Objects.requireNonNull(approvalId, "承認IDはnullにできません");
    }
}
