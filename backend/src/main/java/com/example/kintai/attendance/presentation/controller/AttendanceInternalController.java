package com.example.kintai.attendance.presentation.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.kintai.attendance.application.command.AttendanceUseCase;
import com.example.kintai.attendance.domain.model.AttendanceRecord;
import com.example.kintai.attendance.domain.model.AttendanceStatus;
import com.example.kintai.attendance.domain.model.AttendanceType;
import com.example.kintai.attendance.domain.model.ClockCorrection;
import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.attendance.domain.model.ClockType;
import com.example.kintai.attendance.domain.model.ManualAttendance;
import com.example.kintai.attendance.domain.model.WorkDate;
import com.example.kintai.attendance.domain.repository.AttendanceRecordRepository;
import com.example.kintai.attendance.presentation.dto.CorrectClockRequest;
import com.example.kintai.attendance.presentation.dto.CorrectClockResponse;
import com.example.kintai.attendance.presentation.dto.FinalizeRequest;
import com.example.kintai.attendance.presentation.dto.FinalizeResponse;
import com.example.kintai.attendance.presentation.dto.RegisterManualAttendanceRequest;
import com.example.kintai.attendance.presentation.dto.RegisterManualAttendanceResponse;
import com.example.kintai.shared.domain.model.ApprovalId;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.MonthlyClosingId;
import com.example.kintai.shared.domain.model.ShiftPatternId;

import jakarta.validation.Valid;

/**
 * 勤怠記録内部コントローラー — サービス間通信用の3つのAPIエンドポイントを提供する
 *
 * <p>対応エンドポイント:
 * <ul>
 *   <li>POST /api/v1/internal/attendances/correct — 承認済み打刻修正の適用</li>
 *   <li>POST /api/v1/internal/attendances/register — 承認済み手動勤務登録</li>
 *   <li>POST /api/v1/internal/attendances/finalize — 月次本締め確定</li>
 * </ul>
 * </p>
 *
 * <p>これらのエンドポイントは内部サービス（承認サービス、月次締めサービス等）から
 * 呼び出される想定で、外部ユーザーが直接叩くことはない。
 * 将来的にサービス間認証（APIキー等）を導入予定。</p>
 *
 * <p>認可: 全メソッド ADMIN ロール必須</p>
 */
@RestController
@RequestMapping("/api/v1/internal/attendances")
public class AttendanceInternalController {

    private static final Logger log = LoggerFactory.getLogger(AttendanceInternalController.class);

    /** 勤怠記録ユースケース — ドメインロジックの実行を委譲する */
    private final AttendanceUseCase commandService;

    /** 勤怠記録リポジトリ — レスポンス組み立てのためのレコード再取得用 */
    private final AttendanceRecordRepository attendanceRecordRepository;

    public AttendanceInternalController(
            AttendanceUseCase commandService,
            AttendanceRecordRepository attendanceRecordRepository) {
        this.commandService = commandService;
        this.attendanceRecordRepository = attendanceRecordRepository;
    }

    // ========================================
    // POST /correct — 承認済み打刻修正の適用
    // ========================================

    /**
     * 承認済みの打刻修正を適用する
     *
     * <p>承認サービスから呼び出される。打刻時刻を修正し、
     * 退勤済みの場合は勤務時間を自動再計算する。</p>
     *
     * @param request 打刻修正リクエスト（attendanceId, targetType, correctedTime, reason, approvalId）
     * @return 修正後の勤怠記録の状態
     */
    @PostMapping("/correct")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CorrectClockResponse> correctClock(
            @Valid @RequestBody CorrectClockRequest request) {

        log.debug("打刻修正リクエスト受信: attendanceId={}, targetType={}",
                request.attendanceId(), request.targetType());

        // リクエストをドメインオブジェクトに変換する
        AttendanceRecordId attendanceId = AttendanceRecordId.of(request.attendanceId());
        ClockType targetType = parseClockType(request.targetType());
        ClockTime correctedTime = new ClockTime(request.correctedTime().toInstant());
        ApprovalId approvalId = ApprovalId.of(request.approvalId());

        // ClockCorrection値オブジェクトを生成する
        ClockCorrection correction = new ClockCorrection(
                targetType, correctedTime, request.reason(), approvalId);

        // アプリケーションサービスを呼び出す（修正 → 必要に応じて再計算 → 保存）
        commandService.correctClock(attendanceId, correction);

        // 保存後のレコードを取得してレスポンスを組み立てる
        AttendanceRecord record = findRecordOrThrow(attendanceId);

        // レスポンスDTO組み立て（EmployeeId: String→UUID変換）
        CorrectClockResponse response = new CorrectClockResponse(
                record.getId().value(),
                record.getEmployeeId().value(),
                record.getWorkDate().value(),
                record.getStatus().name(),
                targetType.name(),
                correctedTime.value(),
                // 退勤済みの場合のみ勤務時間を返却する（未退勤はnull）
                record.getStatus() == AttendanceStatus.CLOCKED_OUT && record.getWorkDuration() != null
                        ? record.getWorkDuration().netWorkMinutes() : null,
                record.getStatus() == AttendanceStatus.CLOCKED_OUT && record.getOvertimeDuration() != null
                        ? record.getOvertimeDuration().totalOvertimeMinutes() : null,
                record.getUpdatedAt()
        );

        log.debug("打刻修正完了: attendanceId={}", attendanceId.value());
        return ResponseEntity.ok(response);
    }

    // ========================================
    // POST /register — 承認済み手動勤務登録
    // ========================================

    /**
     * 承認済みの手動勤務実績を登録する
     *
     * <p>承認サービスから呼び出される。出勤+退勤を一括登録し、
     * 勤務時間を自動計算する。</p>
     *
     * @param request 手動勤務登録リクエスト（employeeId, workDate, startTime, endTime, type, reason, approvalId）
     * @return 登録後の勤怠記録の状態（勤務時間計算済み）
     */
    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RegisterManualAttendanceResponse> registerManualAttendance(
            @Valid @RequestBody RegisterManualAttendanceRequest request) {

        log.debug("手動勤務登録リクエスト受信: employeeId={}, workDate={}",
                request.employeeId(), request.workDate());

        // リクエストをドメインオブジェクトに変換する
        EmployeeId employeeId = EmployeeId.of(request.employeeId());
        WorkDate workDate = new WorkDate(request.workDate());
        ClockTime startTime = new ClockTime(request.startTime().toInstant());
        ClockTime endTime = new ClockTime(request.endTime().toInstant());
        ApprovalId approvalId = ApprovalId.of(request.approvalId());
        ShiftPatternId shiftPatternId = request.shiftPatternId() != null
                ? ShiftPatternId.of(request.shiftPatternId()) : null;

        // リクエストの勤務種別文字列をAttendanceType enumに変換する
        AttendanceType attendanceType = parseAttendanceType(request.type());

        // ManualAttendance値オブジェクトを生成する
        ManualAttendance manual = new ManualAttendance(
                startTime, endTime, attendanceType, request.reason(), approvalId);

        // アプリケーションサービスを呼び出す（手動登録 → 勤務時間計算 → 保存）
        AttendanceRecordId attendanceId = commandService.registerManualAttendance(
                employeeId, workDate, manual, shiftPatternId);

        // 保存後のレコードを取得してレスポンスを組み立てる
        AttendanceRecord record = findRecordOrThrow(attendanceId);

        // レスポンスDTO組み立て（EmployeeId: String→UUID変換）
        RegisterManualAttendanceResponse response = new RegisterManualAttendanceResponse(
                record.getId().value(),
                record.getEmployeeId().value(),
                record.getWorkDate().value(),
                record.getStatus().name(),
                startTime.value(),
                endTime.value(),
                record.getWorkDuration().breakMinutes(),
                record.getWorkDuration().netWorkMinutes(),
                record.getOvertimeDuration().totalOvertimeMinutes(),
                record.getUpdatedAt()
        );

        log.debug("手動勤務登録完了: attendanceId={}", attendanceId.value());
        return ResponseEntity.ok(response);
    }

    // ========================================
    // POST /finalize — 月次本締め確定
    // ========================================

    /**
     * 月次本締めにより勤怠記録を確定する
     *
     * <p>月次締めサービスから呼び出される。ステータスをCLOCKED_OUT→FINALIZEDに遷移させ、
     * 以降の変更を不可にする。</p>
     *
     * @param request 本締めリクエスト（attendanceId, monthlyClosingId）
     * @return 確定後の勤怠記録の状態
     */
    @PostMapping("/finalize")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FinalizeResponse> finalizeRecord(
            @Valid @RequestBody FinalizeRequest request) {

        log.debug("本締め確定リクエスト受信: attendanceId={}, monthlyClosingId={}",
                request.attendanceId(), request.monthlyClosingId());

        // リクエストをドメインオブジェクトに変換する
        AttendanceRecordId attendanceId = AttendanceRecordId.of(request.attendanceId());
        MonthlyClosingId monthlyClosingId = MonthlyClosingId.of(request.monthlyClosingId());

        // アプリケーションサービスを呼び出す（CLOCKED_OUT→FINALIZED → 保存）
        commandService.finalizeRecord(attendanceId, monthlyClosingId);

        // 保存後のレコードを取得してレスポンスを組み立てる
        AttendanceRecord record = findRecordOrThrow(attendanceId);

        // レスポンスDTO組み立て（EmployeeId: String→UUID変換）
        FinalizeResponse response = new FinalizeResponse(
                record.getId().value(),
                record.getEmployeeId().value(),
                record.getWorkDate().value(),
                record.getStatus().name(),
                request.monthlyClosingId(),
                record.getUpdatedAt()
        );

        log.debug("本締め確定完了: attendanceId={}", attendanceId.value());
        return ResponseEntity.ok(response);
    }

    // ========================================
    // ヘルパーメソッド
    // ========================================

    /**
     * 打刻種別文字列をClockType enumに変換する
     *
     * <p>無効な値が指定された場合は分かりやすいエラーメッセージを返す。</p>
     *
     * @param type 打刻種別文字列（"CLOCK_IN" / "CLOCK_OUT" / "BREAK_START" / "BREAK_END"）
     * @return ClockType enum値
     * @throws IllegalArgumentException 無効な打刻種別の場合
     */
    private ClockType parseClockType(String type) {
        try {
            return ClockType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "無効な打刻種別です: " + type
                            + "（CLOCK_IN, CLOCK_OUT, BREAK_START, BREAK_END のいずれかを指定してください）");
        }
    }

    /**
     * 勤務種別文字列をAttendanceType enumに変換する
     *
     * <p>無効な値が指定された場合は分かりやすいエラーメッセージを返す。</p>
     *
     * @param type 勤務種別文字列（"NORMAL" / "BUSINESS_TRIP" / "REMOTE" / "PAID_LEAVE" / "ABSENCE"）
     * @return AttendanceType enum値
     * @throws IllegalArgumentException 無効な勤務種別の場合
     */
    private AttendanceType parseAttendanceType(String type) {
        try {
            return AttendanceType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "無効な勤務種別です: " + type
                            + "（NORMAL, BUSINESS_TRIP, REMOTE, PAID_LEAVE, ABSENCE のいずれかを指定してください）");
        }
    }

    /**
     * 勤怠記録IDでレコードを取得する（見つからない場合はシステムエラー）
     *
     * @param id 勤怠記録ID
     * @return 勤怠記録
     * @throws IllegalStateException 保存直後のレコードが取得できない場合
     */
    private AttendanceRecord findRecordOrThrow(AttendanceRecordId id) {
        return attendanceRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "保存直後のレコードが取得できません: attendanceId=" + id.value()));
    }
}
