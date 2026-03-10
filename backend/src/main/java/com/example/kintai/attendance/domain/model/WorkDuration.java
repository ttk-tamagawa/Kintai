package com.example.kintai.attendance.domain.model;

/**
 * 勤務時間VO — 1勤務日の勤務時間内訳を表す値オブジェクト
 *
 * <p>勤務時間の4つの内訳を分単位で保持する。
 * 退勤打刻後にドメインサービス（WorkDurationCalculator）により計算され、
 * 勤怠記録に設定される。打刻修正時には再計算される。</p>
 *
 * @param scheduledMinutes 所定勤務時間（分）— シフトで定められた勤務時間
 * @param actualMinutes    実勤務時間（分）— 出勤から退勤までの総時間
 * @param breakMinutes     休憩時間（分）— 休憩の合計時間
 * @param netWorkMinutes   実労働時間（分）— actualMinutes - breakMinutes
 */
public record WorkDuration(
        int scheduledMinutes,
        int actualMinutes,
        int breakMinutes,
        int netWorkMinutes
) {

    /**
     * 4つの時間を指定して勤務時間を生成するファクトリメソッド
     *
     * @param scheduled 所定勤務時間（分）
     * @param actual    実勤務時間（分）
     * @param breakMin  休憩時間（分）
     * @param netWork   実労働時間（分）
     * @return WorkDurationインスタンス
     */
    public static WorkDuration of(int scheduled, int actual, int breakMin, int netWork) {
        return new WorkDuration(scheduled, actual, breakMin, netWork);
    }

    /**
     * 全てゼロの勤務時間を生成するファクトリメソッド
     *
     * <p>勤怠記録の初期状態や、打刻前の状態で使用する。</p>
     *
     * @return 全項目がゼロのWorkDuration
     */
    public static WorkDuration zero() {
        return new WorkDuration(0, 0, 0, 0);
    }
}
