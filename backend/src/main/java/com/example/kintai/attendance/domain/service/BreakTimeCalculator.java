package com.example.kintai.attendance.domain.service;

import com.example.kintai.attendance.domain.model.ClockEntry;
import com.example.kintai.attendance.domain.model.ClockType;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 休憩時間計算サービス — 打刻エントリから休憩時間を算出する
 *
 * <p>BREAK_STARTとBREAK_ENDのペアから休憩時間を計算する。
 * 複数回の休憩がある場合は全てを合算する。
 * 不変条件INV-ATT-005（BREAK_STARTとBREAK_ENDの対）を検証する。</p>
 *
 * <p>計算精度: 分単位（秒以下は切り捨て）</p>
 */
@Service
public class BreakTimeCalculator {

    /**
     * 打刻エントリ一覧から休憩時間の合計（分）を計算する
     *
     * <p>計算手順:
     * <ol>
     *   <li>BREAK_STARTとBREAK_ENDを時系列で対にする</li>
     *   <li>各ペアの時間差（分）を算出する</li>
     *   <li>全ペアの合計を返す</li>
     * </ol>
     * </p>
     *
     * @param clockEntries 打刻エントリ一覧
     * @return 休憩時間の合計（分）
     * @throws IllegalStateException BREAK_STARTとBREAK_ENDが対になっていない場合
     */
    public int calculateTotalBreakMinutes(List<ClockEntry> clockEntries) {
        Objects.requireNonNull(clockEntries, "打刻エントリ一覧はnullにできません");

        // BREAK_STARTとBREAK_ENDをそれぞれ時系列順に抽出する
        List<Instant> breakStarts = new ArrayList<>();
        List<Instant> breakEnds = new ArrayList<>();

        for (ClockEntry entry : clockEntries) {
            if (entry.type() == ClockType.BREAK_START) {
                breakStarts.add(entry.time().value());
            } else if (entry.type() == ClockType.BREAK_END) {
                breakEnds.add(entry.time().value());
            }
        }

        // ペア検証: BREAK_ENDの数がBREAK_STARTの数を超えていないことを確認
        if (breakEnds.size() > breakStarts.size()) {
            throw new IllegalStateException(
                    "休憩終了の数(" + breakEnds.size() + ")が休憩開始の数(" + breakStarts.size() + ")を超えています"
            );
        }

        // 各ペアの時間差を合算する（完了した休憩のみ）
        int totalBreakMinutes = 0;
        for (int i = 0; i < breakEnds.size(); i++) {
            // 休憩開始〜終了の差分を分単位で計算（秒以下は切り捨て）
            long minutes = Duration.between(breakStarts.get(i), breakEnds.get(i)).toMinutes();
            totalBreakMinutes += (int) minutes;
        }

        return totalBreakMinutes;
    }
}
