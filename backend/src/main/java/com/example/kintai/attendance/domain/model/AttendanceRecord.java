package com.example.kintai.attendance.domain.model;

import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.MonthlyClosingId;
import com.example.kintai.shared.domain.model.ShiftPatternId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 勤怠記録 — 集約ルート
 *
 * <p>従業員の1勤務日の出退勤・休憩の打刻を管理し、勤務時間を計算する。
 * 勤怠管理システムの中核エンティティであり、以下の7つのコマンドを持つ。</p>
 *
 * <ul>
 *   <li>出勤打刻する（clockIn）</li>
 *   <li>退勤打刻する（clockOut）</li>
 *   <li>休憩開始する（startBreak）</li>
 *   <li>休憩終了する（endBreak）</li>
 *   <li>打刻を修正する（correctClock）</li>
 *   <li>勤務実績を登録する（registerManualAttendance）</li>
 *   <li>本締め確定する（finalize）</li>
 * </ul>
 *
 * <p>不変条件:
 * <ul>
 *   <li>INV-ATT-001: 出勤時刻 &lt; 退勤時刻</li>
 *   <li>INV-ATT-002: 休憩は出勤-退勤の間</li>
 *   <li>INV-ATT-003: FINALIZED後は変更不可</li>
 *   <li>INV-ATT-004: 1勤務日1レコード</li>
 *   <li>INV-ATT-005: 休憩はBREAK_START→BREAK_ENDの対</li>
 * </ul>
 * </p>
 */
public class AttendanceRecord {

    /** 勤怠記録ID */
    private final AttendanceRecordId id;

    /** 従業員ID */
    private final EmployeeId employeeId;

    /** 勤務日 */
    private final WorkDate workDate;

    /** シフトパターンID（nullable: シフト未割当の場合） */
    private final ShiftPatternId shiftPatternId;

    /** 勤怠ステータス — 状態遷移を管理する */
    private AttendanceStatus status;

    /** 打刻エントリ一覧 — イミュータブルな打刻ログ（追記のみ） */
    private final List<ClockEntry> clockEntries;

    /** 勤務時間 — 退勤後に計算される */
    private WorkDuration workDuration;

    /** 残業時間 — 退勤後に計算される */
    private OvertimeDuration overtimeDuration;

    /** 楽観的ロック用バージョン */
    private int version;

    /** 作成日時 */
    private final Instant createdAt;

    /** 更新日時 */
    private Instant updatedAt;

    // ========================
    // コンストラクタ
    // ========================

    /**
     * パッケージプライベートコンストラクタ — リポジトリからの復元用
     *
     * <p>DBから読み込んだデータをそのまま設定するため、
     * ガード条件のチェックは行わない。</p>
     *
     * @param id              勤怠記録ID
     * @param employeeId      従業員ID
     * @param workDate        勤務日
     * @param shiftPatternId  シフトパターンID（nullable）
     * @param status          勤怠ステータス
     * @param clockEntries    打刻エントリ一覧
     * @param workDuration    勤務時間
     * @param overtimeDuration 残業時間
     * @param version         楽観ロックバージョン
     * @param createdAt       作成日時
     * @param updatedAt       更新日時
     */
    AttendanceRecord(
            AttendanceRecordId id,
            EmployeeId employeeId,
            WorkDate workDate,
            ShiftPatternId shiftPatternId,
            AttendanceStatus status,
            List<ClockEntry> clockEntries,
            WorkDuration workDuration,
            OvertimeDuration overtimeDuration,
            int version,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "勤怠記録IDはnullにできません");
        this.employeeId = Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        this.workDate = Objects.requireNonNull(workDate, "勤務日はnullにできません");
        this.shiftPatternId = shiftPatternId; // nullable: シフト未割当の場合
        this.status = Objects.requireNonNull(status, "勤怠ステータスはnullにできません");
        this.clockEntries = new ArrayList<>(
                Objects.requireNonNull(clockEntries, "打刻エントリ一覧はnullにできません")
        );
        this.workDuration = Objects.requireNonNull(workDuration, "勤務時間はnullにできません");
        this.overtimeDuration = Objects.requireNonNull(overtimeDuration, "残業時間はnullにできません");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "作成日時はnullにできません");
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新日時はnullにできません");
    }

    // ========================
    // ファクトリメソッド
    // ========================

    /**
     * 新規勤怠記録を作成する — 公開ファクトリメソッド
     *
     * <p>勤務日に対する勤怠記録を初期状態（NOT_CLOCKED）で生成する。
     * IDは自動生成され、勤務時間・残業時間はゼロで初期化される。</p>
     *
     * @param employeeId     従業員ID
     * @param workDate       勤務日
     * @param shiftPatternId シフトパターンID（nullable）
     * @return 初期状態のAttendanceRecord
     */
    public static AttendanceRecord create(
            EmployeeId employeeId,
            WorkDate workDate,
            ShiftPatternId shiftPatternId
    ) {
        Instant now = Instant.now();
        return new AttendanceRecord(
                AttendanceRecordId.generate(),
                employeeId,
                workDate,
                shiftPatternId,
                AttendanceStatus.NOT_CLOCKED,
                new ArrayList<>(),
                WorkDuration.zero(),
                OvertimeDuration.zero(),
                0,
                now,
                now
        );
    }

    // ========================
    // DB復元用ファクトリメソッド
    // ========================

    /**
     * DBから読み込んだデータで勤怠記録を復元する — リポジトリ実装専用
     *
     * <p>パッケージプライベートコンストラクタを呼び出すためのpublic静的メソッド。
     * インフラ層のリポジトリ実装がドメインオブジェクトを復元する際に使用する。
     * ガード条件のチェックは行わない（DB上のデータは整合性が保証されている）。</p>
     */
    public static AttendanceRecord reconstruct(
            AttendanceRecordId id,
            EmployeeId employeeId,
            WorkDate workDate,
            ShiftPatternId shiftPatternId,
            AttendanceStatus status,
            List<ClockEntry> clockEntries,
            WorkDuration workDuration,
            OvertimeDuration overtimeDuration,
            int version,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new AttendanceRecord(
                id, employeeId, workDate, shiftPatternId,
                status, clockEntries, workDuration, overtimeDuration,
                version, createdAt, updatedAt
        );
    }

    // ========================
    // コマンドメソッド（7つ）
    // ========================

    /**
     * 1. 出勤打刻する
     *
     * <p>ガード条件: ステータスがNOT_CLOCKEDであること。
     * 出勤打刻エントリを追加し、ステータスをCLOCKED_INに遷移させる。</p>
     *
     * @param time   打刻時刻
     * @param source 打刻元（WEBまたはMOBILE）
     * @throws IllegalStateException ステータスがNOT_CLOCKEDでない場合
     */
    public void clockIn(ClockTime time, ClockSource source) {
        // ガード条件: 未打刻状態であることを確認
        if (status != AttendanceStatus.NOT_CLOCKED) {
            throw new IllegalStateException(
                    "出勤打刻できません。現在のステータス: " + status + "（NOT_CLOCKEDである必要があります）"
            );
        }

        // 出勤打刻エントリを追加
        clockEntries.add(new ClockEntry(ClockType.CLOCK_IN, time, source));

        // ステータスを出勤中に遷移
        status = AttendanceStatus.CLOCKED_IN;
        updatedAt = Instant.now();
    }

    /**
     * 2. 退勤打刻する
     *
     * <p>ガード条件: ステータスがCLOCKED_INであること。
     * 退勤打刻エントリを追加し、ステータスをCLOCKED_OUTに遷移させる。
     * 退勤後に勤務時間の計算が必要（後続処理として実施）。</p>
     *
     * @param time   打刻時刻
     * @param source 打刻元（WEBまたはMOBILE）
     * @throws IllegalStateException ステータスがCLOCKED_INでない場合
     */
    public void clockOut(ClockTime time, ClockSource source) {
        // ガード条件: 出勤中であることを確認
        if (status != AttendanceStatus.CLOCKED_IN) {
            throw new IllegalStateException(
                    "退勤打刻できません。現在のステータス: " + status + "（CLOCKED_INである必要があります）"
            );
        }

        // INV-ATT-001: 退勤時刻は出勤時刻より後であること
        ClockTime clockInTime = getEffectiveClockInTime();
        if (clockInTime != null && !time.value().isAfter(clockInTime.value())) {
            throw new IllegalStateException(
                    "退勤時刻は出勤時刻より後である必要があります（INV-ATT-001）。出勤: "
                            + clockInTime.value() + ", 退勤: " + time.value()
            );
        }

        // 退勤打刻エントリを追加
        clockEntries.add(new ClockEntry(ClockType.CLOCK_OUT, time, source));

        // ステータスを退勤済みに遷移
        status = AttendanceStatus.CLOCKED_OUT;
        updatedAt = Instant.now();
    }

    /**
     * 3. 休憩開始する
     *
     * <p>ガード条件:
     * <ul>
     *   <li>ステータスがCLOCKED_INであること</li>
     *   <li>現在休憩中でないこと（未終了のBREAK_STARTがないこと）</li>
     * </ul>
     * </p>
     *
     * @param time   打刻時刻
     * @param source 打刻元（WEBまたはMOBILE）
     * @throws IllegalStateException ガード条件を満たさない場合
     */
    public void startBreak(ClockTime time, ClockSource source) {
        // ガード条件1: 出勤中であることを確認
        if (status != AttendanceStatus.CLOCKED_IN) {
            throw new IllegalStateException(
                    "休憩を開始できません。現在のステータス: " + status + "（CLOCKED_INである必要があります）"
            );
        }

        // ガード条件2: すでに休憩中でないことを確認（INV-ATT-005）
        if (isOnBreak()) {
            throw new IllegalStateException(
                    "休憩を開始できません。すでに休憩中です（未終了のBREAK_STARTがあります）"
            );
        }

        // INV-ATT-002: 休憩開始時刻は出勤時刻より後であること
        ClockTime clockInTime = getEffectiveClockInTime();
        if (clockInTime != null && !time.value().isAfter(clockInTime.value())) {
            throw new IllegalStateException(
                    "休憩開始時刻は出勤時刻より後である必要があります（INV-ATT-002）。出勤: "
                            + clockInTime.value() + ", 休憩開始: " + time.value()
            );
        }

        // 休憩開始エントリを追加（ステータスはCLOCKED_INのまま）
        clockEntries.add(new ClockEntry(ClockType.BREAK_START, time, source));
        updatedAt = Instant.now();
    }

    /**
     * 4. 休憩終了する
     *
     * <p>ガード条件:
     * <ul>
     *   <li>ステータスがCLOCKED_INであること</li>
     *   <li>現在休憩中であること（未終了のBREAK_STARTがあること）</li>
     * </ul>
     * </p>
     *
     * @param time   打刻時刻
     * @param source 打刻元（WEBまたはMOBILE）
     * @throws IllegalStateException ガード条件を満たさない場合
     */
    public void endBreak(ClockTime time, ClockSource source) {
        // ガード条件1: 出勤中であることを確認
        if (status != AttendanceStatus.CLOCKED_IN) {
            throw new IllegalStateException(
                    "休憩を終了できません。現在のステータス: " + status + "（CLOCKED_INである必要があります）"
            );
        }

        // ガード条件2: 休憩中であることを確認（INV-ATT-005）
        if (!isOnBreak()) {
            throw new IllegalStateException(
                    "休憩を終了できません。現在休憩中ではありません（未終了のBREAK_STARTがありません）"
            );
        }

        // INV-ATT-002: 休憩終了時刻は休憩開始時刻より後であること
        ClockTime breakStartTime = getLastBreakStartTime();
        if (breakStartTime != null && !time.value().isAfter(breakStartTime.value())) {
            throw new IllegalStateException(
                    "休憩終了時刻は休憩開始時刻より後である必要があります（INV-ATT-002）。休憩開始: "
                            + breakStartTime.value() + ", 休憩終了: " + time.value()
            );
        }

        // 休憩終了エントリを追加（ステータスはCLOCKED_INのまま）
        clockEntries.add(new ClockEntry(ClockType.BREAK_END, time, source));
        updatedAt = Instant.now();
    }

    /**
     * 5. 打刻を修正する
     *
     * <p>ガード条件: ステータスがCLOCKED_OUTであること。
     * 設計書の状態遷移表に基づき、退勤済み状態でのみ打刻修正を許可する。
     * 承認済みの修正内容に基づき、source=CORRECTIONの新しい打刻エントリを追加する。
     * 元の打刻は保持されたまま残る（履歴追跡のため）。</p>
     *
     * @param correction 打刻修正内容（承認済み）
     * @throws IllegalStateException ステータスがCLOCKED_OUTでない場合
     */
    public void correctClock(ClockCorrection correction) {
        // ガード条件: 退勤済み状態であることを確認（設計書: CLOCKED_OUT → CLOCKED_OUT の自己遷移のみ許可）
        if (status != AttendanceStatus.CLOCKED_OUT) {
            throw new IllegalStateException(
                    "打刻を修正できません。現在のステータス: " + status + "（CLOCKED_OUTである必要があります）"
            );
        }

        // 修正エントリを追加（source=CORRECTIONで新規追加。元の打刻は保持）
        clockEntries.add(new ClockEntry(
                correction.targetType(),
                correction.correctedTime(),
                ClockSource.CORRECTION
        ));
        updatedAt = Instant.now();
    }

    /**
     * 6. 勤務実績を登録する
     *
     * <p>ガード条件: ステータスがNOT_CLOCKEDであること。
     * 承認済みの手動勤務登録申請に基づき、出勤・退勤エントリを一括登録する。
     * ステータスをCLOCKED_OUTに遷移させる（打刻→退勤を一度に行う）。</p>
     *
     * @param manual 手動勤務登録内容（承認済み）
     * @throws IllegalStateException ステータスがNOT_CLOCKEDでない場合
     */
    public void registerManualAttendance(ManualAttendance manual) {
        // ガード条件: 未打刻状態であることを確認
        if (status != AttendanceStatus.NOT_CLOCKED) {
            throw new IllegalStateException(
                    "勤務実績を登録できません。現在のステータス: " + status + "（NOT_CLOCKEDである必要があります）"
            );
        }

        // INV-ATT-001: 終了時刻は開始時刻より後であること
        if (!manual.endTime().value().isAfter(manual.startTime().value())) {
            throw new IllegalStateException(
                    "勤務終了時刻は勤務開始時刻より後である必要があります（INV-ATT-001）。開始: "
                            + manual.startTime().value() + ", 終了: " + manual.endTime().value()
            );
        }

        // 出勤エントリを追加（source=MANUALで手動登録として記録）
        clockEntries.add(new ClockEntry(
                ClockType.CLOCK_IN,
                manual.startTime(),
                ClockSource.MANUAL
        ));

        // 退勤エントリを追加（source=MANUALで手動登録として記録）
        clockEntries.add(new ClockEntry(
                ClockType.CLOCK_OUT,
                manual.endTime(),
                ClockSource.MANUAL
        ));

        // ステータスを退勤済みに遷移（出勤→退勤を一度に行う）
        status = AttendanceStatus.CLOCKED_OUT;
        updatedAt = Instant.now();
    }

    /**
     * 7. 本締め確定する
     *
     * <p>ガード条件: ステータスがCLOCKED_OUTであること。
     * 月次締めSagaからの内部呼び出しにより、勤怠記録を確定状態にする。
     * 確定後は一切の変更が不可となる（INV-ATT-003）。</p>
     *
     * @param monthlyClosingId 月次締めID
     * @throws IllegalStateException ステータスがCLOCKED_OUTでない場合
     */
    public void finalizeRecord(MonthlyClosingId monthlyClosingId) {
        // ガード条件: 退勤済みであることを確認
        if (status != AttendanceStatus.CLOCKED_OUT) {
            throw new IllegalStateException(
                    "本締め確定できません。現在のステータス: " + status + "（CLOCKED_OUTである必要があります）"
            );
        }

        // ステータスを確定済みに遷移
        status = AttendanceStatus.FINALIZED;
        updatedAt = Instant.now();
    }

    // ========================
    // 計算メソッド
    // ========================

    /**
     * 勤務時間を設定する — ドメインサービスによる計算結果を受け取る
     *
     * <p>WorkDurationCalculatorが計算した勤務時間と残業時間を設定する。
     * 退勤打刻後や打刻修正後に呼び出される。</p>
     *
     * @param duration 計算された勤務時間
     * @param overtime 計算された残業時間
     */
    public void calculateWorkDuration(WorkDuration duration, OvertimeDuration overtime) {
        Objects.requireNonNull(duration, "勤務時間はnullにできません");
        Objects.requireNonNull(overtime, "残業時間はnullにできません");

        this.workDuration = duration;
        this.overtimeDuration = overtime;
        this.updatedAt = Instant.now();
    }

    // ========================
    // ヘルパーメソッド
    // ========================

    /**
     * 有効な出勤時刻を取得する — 最後のCLOCK_INエントリの時刻を返す
     *
     * <p>打刻修正（CORRECTION）がある場合、最後に追加されたCLOCK_INエントリが
     * 有効な出勤時刻となる。INV-ATT-001/002の検証に使用する。</p>
     *
     * @return 出勤時刻（CLOCK_INエントリがない場合はnull）
     */
    private ClockTime getEffectiveClockInTime() {
        return clockEntries.stream()
                .filter(entry -> entry.type() == ClockType.CLOCK_IN)
                .reduce((first, second) -> second)
                .map(ClockEntry::time)
                .orElse(null);
    }

    /**
     * 最後の休憩開始時刻を取得する — 最後のBREAK_STARTエントリの時刻を返す
     *
     * <p>INV-ATT-002の検証で、休憩終了時刻が休憩開始時刻より後であることを
     * 確認するために使用する。</p>
     *
     * @return 最後の休憩開始時刻（BREAK_STARTエントリがない場合はnull）
     */
    private ClockTime getLastBreakStartTime() {
        return clockEntries.stream()
                .filter(entry -> entry.type() == ClockType.BREAK_START)
                .reduce((first, second) -> second)
                .map(ClockEntry::time)
                .orElse(null);
    }

    /**
     * 現在休憩中かどうかを判定する
     *
     * <p>打刻エントリ一覧からBREAK_STARTとBREAK_ENDの数を比較し、
     * BREAK_STARTが多い場合は休憩中と判定する（INV-ATT-005）。</p>
     *
     * @return 休憩中の場合true
     */
    public boolean isOnBreak() {
        // BREAK_STARTとBREAK_ENDのカウントを比較
        long breakStartCount = clockEntries.stream()
                .filter(entry -> entry.type() == ClockType.BREAK_START)
                .count();
        long breakEndCount = clockEntries.stream()
                .filter(entry -> entry.type() == ClockType.BREAK_END)
                .count();

        // BREAK_STARTがBREAK_ENDより多い場合、未終了の休憩がある
        return breakStartCount > breakEndCount;
    }

    // ========================
    // ゲッターメソッド
    // ========================

    /** 勤怠記録IDを取得する */
    public AttendanceRecordId getId() {
        return id;
    }

    /** 従業員IDを取得する */
    public EmployeeId getEmployeeId() {
        return employeeId;
    }

    /** 勤務日を取得する */
    public WorkDate getWorkDate() {
        return workDate;
    }

    /** シフトパターンIDを取得する（nullable） */
    public ShiftPatternId getShiftPatternId() {
        return shiftPatternId;
    }

    /** 勤怠ステータスを取得する */
    public AttendanceStatus getStatus() {
        return status;
    }

    /**
     * 打刻エントリ一覧を取得する（変更不可のリスト）
     *
     * <p>外部からの変更を防ぐため、unmodifiableなリストを返す。
     * 打刻エントリの追加はコマンドメソッド経由でのみ行う。</p>
     */
    public List<ClockEntry> getClockEntries() {
        return Collections.unmodifiableList(clockEntries);
    }

    /** 勤務時間を取得する */
    public WorkDuration getWorkDuration() {
        return workDuration;
    }

    /** 残業時間を取得する */
    public OvertimeDuration getOvertimeDuration() {
        return overtimeDuration;
    }

    /** 楽観ロックバージョンを取得する */
    public int getVersion() {
        return version;
    }

    /** 作成日時を取得する */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** 更新日時を取得する */
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
