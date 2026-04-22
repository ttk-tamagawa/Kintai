package com.example.kintai.attendance.application.query;

import com.example.kintai.attendance.application.query.AttendanceFinder.MonthlySummary;
import com.example.kintai.shared.kernel.contract.QueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 月次勤怠サマリーCSV出力クエリサービス（UC-ATT-Q04）— CSV形式で出力する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>全従業員データを取得する（ページネーションなし）</li>
 *   <li>CSV文字列を組み立てる</li>
 *   <li>UTF-8 BOM付きバイト配列に変換して返却する</li>
 * </ol>
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class ExportMonthlySummaryQueryService implements QueryService<ExportMonthlySummaryQuery, byte[]> {

    private static final Logger log = LoggerFactory.getLogger(ExportMonthlySummaryQueryService.class);

    /** UTF-8 BOM — ExcelでのCSV文字化け防止 */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** Read Model ファインダー */
    private final AttendanceFinder finder;

    public ExportMonthlySummaryQueryService(AttendanceFinder finder) {
        this.finder = finder;
    }

    /**
     * 月次勤怠サマリーをCSV形式で出力する
     *
     * @param query CSV出力クエリ（部門ID、年月）
     * @return CSV形式のバイト配列（UTF-8 BOM付き）
     */
    @Override
    public byte[] execute(ExportMonthlySummaryQuery query) {
        log.debug("月次サマリーCSV: departmentId={}, year={}, month={}",
                query.departmentId(), query.year(), query.month());

        // 全従業員データを取得する（ページネーションなし）
        List<MonthlySummary> summaries =
                finder.findMonthlySummariesByDepartment(query.departmentId(), query.year(), query.month());

        // CSV文字列を組み立てる
        StringBuilder csv = new StringBuilder();
        // ヘッダー行
        csv.append("従業員ID,従業員名,出勤日数,総労働時間,総残業時間,深夜時間,有給消化\n");
        // データ行
        for (MonthlySummary s : summaries) {
            csv.append(s.employeeId()).append(',');
            csv.append(escapeCsv(s.employeeName())).append(',');
            csv.append(s.totalWorkDays()).append(',');
            csv.append(minutesToHours(s.totalWorkMinutes())).append(',');
            csv.append(minutesToHours(s.totalOvertimeMinutes())).append(',');
            csv.append(minutesToHours(s.totalLateNightMinutes())).append(',');
            csv.append(s.paidLeaveUsed()).append('\n');
        }

        // UTF-8 BOM + CSV本文をバイト配列に変換する
        return addBom(csv.toString());
    }

    /** 分を時間（小数第1位）に変換する文字列（CSV用） */
    private String minutesToHours(int minutes) {
        return String.format("%.1f", (double) minutes / 60.0);
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
