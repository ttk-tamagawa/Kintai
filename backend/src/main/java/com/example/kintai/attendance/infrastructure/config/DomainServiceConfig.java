package com.example.kintai.attendance.infrastructure.config;

import com.example.kintai.attendance.domain.service.BreakTimeCalculator;
import com.example.kintai.attendance.domain.service.LateNightDetector;
import com.example.kintai.attendance.domain.service.OvertimeCalculator;
import com.example.kintai.attendance.domain.service.WorkDurationCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ドメインサービス Bean 設定 — ドメイン層のサービスを Spring Bean として登録する
 *
 * <p>ドメインサービスは Spring 非依存のため @Component を付与しない。
 * このConfigurationクラスでBeanとして登録し、DI可能にする。</p>
 */
@Configuration
public class DomainServiceConfig {

    /** 休憩時間計算サービス */
    @Bean
    public BreakTimeCalculator breakTimeCalculator() {
        return new BreakTimeCalculator();
    }

    /** 残業時間計算サービス */
    @Bean
    public OvertimeCalculator overtimeCalculator() {
        return new OvertimeCalculator();
    }

    /** 深夜時間帯判定サービス */
    @Bean
    public LateNightDetector lateNightDetector() {
        return new LateNightDetector();
    }

    /** 勤務時間計算サービス（3つのサービスを組み合わせる） */
    @Bean
    public WorkDurationCalculator workDurationCalculator(
            BreakTimeCalculator breakTimeCalculator,
            OvertimeCalculator overtimeCalculator,
            LateNightDetector lateNightDetector) {
        return new WorkDurationCalculator(breakTimeCalculator, overtimeCalculator, lateNightDetector);
    }
}
