package com.example.kintai.attendance.application.query;

import com.example.kintai.attendance.domain.repository.AttendanceSummaryQueryRepository;
import com.example.kintai.attendance.domain.repository.AttendanceSummaryQueryRepository.DepartmentStats;
import com.example.kintai.shared.kernel.contract.QueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 部門別勤怠ダッシュボードCSV出力クエリサービス（UC-ATT-Q06）— CSV形式で出力する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>全部門統計を取得する</li>
 *   <li>部門IDフィルタを適用する</li>
 *   <li>CSV文字列を組み立てる</li>
 *   <li>UTF-8 BOM付きバイト配列に変換して返却する</li>
 * </ol>
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class ExportDepartmentDashboardQueryService implements QueryService<ExportDepartmentDashboardQuery, byte[]> {

    private static final Logger log = LoggerFactory.getLogger(ExportDepartmentDashboardQueryService.class);

    /** UTF-8 BOM — ExcelでのCSV文字化け防止 */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** Read Modelクエリリポジトリ */
    private final AttendanceSummaryQueryRepository queryRepository;

    public ExportDepartmentDashboardQueryService(AttendanceSummaryQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    /**
     * 部門別勤怠ダッシュボードをCSV形式で出力する
     *
     * @param query CSV出力クエリ（部門ID、年月）
     * @return CSV形式のバイト配列（UTF-8 BOM付き）
     */
    @Override
    public byte[] execute(ExportDepartmentDashboardQuery query) {
        log.debug("部門ダッシュボードCSV: departmentId={}, year={}, month={}",
                query.departmentId(), query.year(), query.month());

        // 全部門統計を取得する
        List<DepartmentStats> stats = queryRepository.findDepartmentStats(query.year(), query.month());

        // 部門IDフィルタが指定されている場合は絞り込む
        if (query.departmentId() != null && !query.departmentId().isEmpty()) {
            stats = stats.stream()
                    .filter(s -> query.departmentId().equals(s.departmentId()))
                    .toList();
        }

        // CSV文字列を組み立てる
        StringBuilder csv = new StringBuilder();
        // ヘッダー行
        csv.append("部署ID,部署名,人数,平均残業時間,最大残業時間,残業アラート件数,打刻漏れ件数,出勤率\n");
        // データ行
        for (DepartmentStats s : stats) {
            csv.append(s.departmentId()).append(',');
            csv.append(escapeCsv(s.departmentName())).append(',');
            csv.append(s.totalEmployees()).append(',');
            csv.append(bigDecimalMinutesToHours(s.avgOvertimeMinutes())).append(',');
            csv.append(minutesToHours(s.maxOvertimeMinutes())).append(',');
            csv.append(s.overtimeAlertCount()).append(',');
            csv.append(s.missingClockCount()).append(',');
            csv.append(formatRate(s.attendanceRate())).append('\n');
        }

        // UTF-8 BOM + CSV本文をバイト配列に変換する
        return addBom(csv.toString());
    }

    /** 分を時間（小数第1位）に変換する文字列（CSV用） */
    private String minutesToHours(int minutes) {
        return String.format("%.1f", (double) minutes / 60.0);
    }

    /** BigDecimalの分を時間（小数第1位）に変換する文字列（CSV用） */
    private String bigDecimalMinutesToHours(BigDecimal minutes) {
        if (minutes == null) return "0.0";
        return String.format("%.1f", minutes.doubleValue() / 60.0);
    }

    /** BigDecimalのレートを小数第1位でフォーマットする（CSV用） */
    private String formatRate(BigDecimal rate) {
        if (rate == null) return "0.0";
        return String.format("%.1f", rate.doubleValue());
    }

    /** CSV値をエスケープする（カンマや改行を含む場合はダブルクォートで囲む） */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** UTF-8 BOM + CSV文字列をバイト配列に変換する */
    private byte[] addBom(String csvContent) {
        byte[] csvBytes = csvContent.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF8_BOM.length + csvBytes.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(csvBytes, 0, result, UTF8_BOM.length, csvBytes.length);
        return result;
    }
}
