package com.example.kintai.attendance.domain.model;

/**
 * 残業時間VO — 1勤務日の残業時間内訳を表す値オブジェクト
 *
 * <p>残業時間を区分別に分単位で保持する。割増率の計算に使用される。
 * <ul>
 *   <li>通常残業（時間外）: ×1.25</li>
 *   <li>深夜残業（22:00-5:00）: ×1.25（時間外+深夜で×1.50）</li>
 *   <li>休日残業（法定休日）: ×1.35（休日+深夜で×1.60）</li>
 * </ul>
 * </p>
 *
 * @param regularOvertimeMinutes 通常残業時間（分）— 所定超過分
 * @param lateNightMinutes       深夜勤務時間（分）— 22:00-5:00の勤務時間
 * @param holidayMinutes         休日勤務時間（分）— 法定休日の勤務時間
 * @param totalOvertimeMinutes   合計残業時間（分）— 全残業時間の合計
 */
public record OvertimeDuration(
        int regularOvertimeMinutes,
        int lateNightMinutes,
        int holidayMinutes,
        int totalOvertimeMinutes
) {

    /**
     * 4つの残業時間を指定して生成するファクトリメソッド
     *
     * @param regular   通常残業時間（分）
     * @param lateNight 深夜勤務時間（分）
     * @param holiday   休日勤務時間（分）
     * @param total     合計残業時間（分）
     * @return OvertimeDurationインスタンス
     */
    public static OvertimeDuration of(int regular, int lateNight, int holiday, int total) {
        return new OvertimeDuration(regular, lateNight, holiday, total);
    }

    /**
     * 全てゼロの残業時間を生成するファクトリメソッド
     *
     * <p>勤怠記録の初期状態や、残業なしの場合に使用する。</p>
     *
     * @return 全項目がゼロのOvertimeDuration
     */
    public static OvertimeDuration zero() {
        return new OvertimeDuration(0, 0, 0, 0);
    }
}
