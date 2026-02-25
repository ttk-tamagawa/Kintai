"use client";

import { useState, useEffect } from "react";
import { AlertTriangle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Modal } from "@/components/ui/modal";
import { StatusBadge } from "@/components/ui/status-badge";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { useToast } from "@/hooks/useToast";
import {
  fetchActivePatterns,
  fetchEmployees,
  createSchedule,
  updateSchedule,
  publishSchedule,
  type EmployeeOption,
} from "./api";
import type { ShiftPattern, ScheduleItem, DayOfWeek } from "@/types";

// ========================================
// シフト割当モーダル（SCR-SHF-004）
// 新規割当 / 編集の2モード対応
// 7曜日のパターン選択 + 公開ボタン
// ========================================

/** 曜日定義 */
const DAYS: { key: DayOfWeek; label: string }[] = [
  { key: "MONDAY", label: "月" },
  { key: "TUESDAY", label: "火" },
  { key: "WEDNESDAY", label: "水" },
  { key: "THURSDAY", label: "木" },
  { key: "FRIDAY", label: "金" },
  { key: "SATURDAY", label: "土" },
  { key: "SUNDAY", label: "日" },
];

/** 「休み」を表す特殊値（Selectの value に使用） */
const REST_VALUE = "__REST__";

/** 全曜日を「休み」で初期化したオブジェクトを作成する */
function createEmptyAssignments(): Record<DayOfWeek, string> {
  return {
    MONDAY: REST_VALUE,
    TUESDAY: REST_VALUE,
    WEDNESDAY: REST_VALUE,
    THURSDAY: REST_VALUE,
    FRIDAY: REST_VALUE,
    SATURDAY: REST_VALUE,
    SUNDAY: REST_VALUE,
  };
}

interface ShiftAssignModalProps {
  /** モーダル表示状態 */
  open: boolean;
  /** 新規割当 or 編集 */
  mode: "create" | "edit";
  /** 編集対象のスケジュール（editモード時） */
  editTarget: ScheduleItem | null;
  /** モーダルを閉じるハンドラー */
  onClose: () => void;
  /** 割当/変更/公開完了時のコールバック */
  onComplete: () => void;
}

export function ShiftAssignModal({
  open,
  mode,
  editTarget,
  onClose,
  onComplete,
}: ShiftAssignModalProps) {
  const toast = useToast();

  // パターン選択肢（ACTIVEのみ）
  const [patterns, setPatterns] = useState<ShiftPattern[]>([]);
  // 従業員選択肢（新規割当時に使用）
  const [employees, setEmployees] = useState<EmployeeOption[]>([]);

  // フォーム入力値
  const [employeeId, setEmployeeId] = useState("");
  const [weekStartDate, setWeekStartDate] = useState("");
  const [assignments, setAssignments] = useState<Record<DayOfWeek, string>>(
    createEmptyAssignments
  );

  // エラー・送信状態
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  // 公開確認ダイアログ
  const [publishConfirmOpen, setPublishConfirmOpen] = useState(false);
  const [publishing, setPublishing] = useState(false);

  // ========================================
  // モーダル表示時の初期化
  // ========================================
  useEffect(() => {
    if (!open) return;

    // 有効なパターン一覧を取得する
    fetchActivePatterns()
      .then(setPatterns)
      .catch(() => toast.error("パターン一覧の取得に失敗しました"));

    // 新規モードでは従業員一覧を取得する
    if (mode === "create") {
      fetchEmployees()
        .then(setEmployees)
        .catch(() => {
          /* 従業員取得失敗は許容（手入力にフォールバック） */
        });
    }

    // 編集モード: 既存データをフォームにプリセットする
    if (mode === "edit" && editTarget) {
      setEmployeeId(editTarget.employeeId);
      setWeekStartDate(editTarget.weekStartDate);
      const preset = createEmptyAssignments();
      for (const day of DAYS) {
        const a = editTarget.assignments[day.key];
        if (a) {
          preset[day.key] = a.patternId;
        }
      }
      setAssignments(preset);
    } else {
      // 新規モード: フォームをリセットする
      setEmployeeId("");
      setWeekStartDate("");
      setAssignments(createEmptyAssignments());
    }

    setErrors({});
  }, [open, mode, editTarget, toast]);

  // ========================================
  // バリデーション
  // ========================================
  const validate = (): boolean => {
    const errs: Record<string, string> = {};

    if (mode === "create") {
      if (!employeeId) {
        errs.employeeId = "従業員を選択してください";
      }
      if (!weekStartDate) {
        errs.weekStartDate = "週開始日を入力してください";
      } else {
        // 月曜日かどうかチェック（getDay: 0=日, 1=月, ..., 6=土）
        const date = new Date(weekStartDate);
        if (date.getDay() !== 1) {
          errs.weekStartDate = "月曜日を選択してください";
        }
      }
    }

    // 少なくとも1日はシフトを割り当てているかチェックする
    const hasAnyAssignment = DAYS.some(
      (d) => assignments[d.key] !== REST_VALUE
    );
    if (!hasAnyAssignment) {
      errs.assignments = "少なくとも1日はシフトを割り当ててください";
    }

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  // ========================================
  // assignments をAPI用のオブジェクトに変換する（休みの曜日を除外）
  // ========================================
  const buildAssignmentsPayload = (): Partial<Record<DayOfWeek, string>> => {
    const result: Partial<Record<DayOfWeek, string>> = {};
    for (const day of DAYS) {
      if (assignments[day.key] !== REST_VALUE) {
        result[day.key] = assignments[day.key];
      }
    }
    return result;
  };

  // ========================================
  // 割当/変更の実行
  // ========================================
  const handleSubmit = async () => {
    if (!validate()) return;
    setSubmitting(true);
    try {
      const payload = buildAssignmentsPayload();
      if (mode === "create") {
        await createSchedule({
          employeeId,
          weekStartDate,
          assignments: payload,
        });
        toast.success("シフトを割り当てました");
      } else if (editTarget) {
        await updateSchedule(editTarget.scheduleId, {
          assignments: payload,
        });
        toast.success("シフトを変更しました");
      }
      onComplete();
    } catch (err: unknown) {
      // 409: 対象週に既存スケジュールが存在する
      const error = err as { response?: { status?: number } };
      if (error?.response?.status === 409) {
        toast.warning("対象週に既存スケジュールが存在します");
      } else {
        toast.apiError(err);
      }
    } finally {
      setSubmitting(false);
    }
  };

  // ========================================
  // 公開の実行
  // ========================================
  const handlePublish = async () => {
    if (!editTarget) return;
    setPublishing(true);
    try {
      await publishSchedule(editTarget.scheduleId);
      toast.success("スケジュールを公開しました");
      setPublishConfirmOpen(false);
      onComplete();
    } catch (err) {
      toast.apiError(err);
    } finally {
      setPublishing(false);
    }
  };

  const isEdit = mode === "edit";
  const isDraft = editTarget?.status === "DRAFT";

  return (
    <>
      <Modal
        open={open}
        onClose={onClose}
        title={isEdit ? "シフト変更" : "シフト割当"}
        className="sm:max-w-[520px]"
        footer={
          <div className="flex w-full justify-end gap-2">
            <Button variant="outline" onClick={onClose} disabled={submitting}>
              キャンセル
            </Button>
            {/* 公開ボタン: 編集モード + DRAFT の場合のみ表示 */}
            {isEdit && isDraft && (
              <Button
                variant="outline"
                className="border-emerald-600 text-emerald-600 hover:bg-emerald-50"
                onClick={() => setPublishConfirmOpen(true)}
                disabled={submitting}
              >
                公開する
              </Button>
            )}
            <Button onClick={handleSubmit} disabled={submitting}>
              {submitting ? "処理中..." : isEdit ? "変更" : "割当"}
            </Button>
          </div>
        }
      >
        <div className="space-y-4">
          {/* 従業員（新規: セレクト or テキスト入力 / 編集: 読取専用） */}
          {isEdit ? (
            <div className="space-y-1">
              <Label className="text-sm font-medium">従業員</Label>
              <p className="text-sm text-muted-foreground">
                {editTarget?.employeeName}
              </p>
            </div>
          ) : (
            <div className="space-y-1">
              <Label className="text-sm font-medium">
                従業員 <span className="text-red-600">*</span>
              </Label>
              {employees.length > 0 ? (
                <Select value={employeeId} onValueChange={setEmployeeId}>
                  <SelectTrigger
                    className={errors.employeeId ? "border-red-600" : ""}
                  >
                    <SelectValue placeholder="従業員を選択" />
                  </SelectTrigger>
                  <SelectContent>
                    {employees.map((emp) => (
                      <SelectItem
                        key={emp.employeeId}
                        value={emp.employeeId}
                      >
                        {emp.employeeName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              ) : (
                <Input
                  value={employeeId}
                  onChange={(e) => setEmployeeId(e.target.value)}
                  placeholder="従業員IDを入力"
                  className={errors.employeeId ? "border-red-600" : ""}
                />
              )}
              {errors.employeeId && (
                <p className="text-xs text-red-600">{errors.employeeId}</p>
              )}
            </div>
          )}

          {/* 週開始日（新規: 入力可 / 編集: 読取専用） */}
          {isEdit ? (
            <div className="space-y-1">
              <Label className="text-sm font-medium">週開始日</Label>
              <p className="text-sm text-muted-foreground">
                {editTarget?.weekStartDate}
              </p>
            </div>
          ) : (
            <div className="space-y-1">
              <Label className="text-sm font-medium">
                週開始日（月曜日）<span className="text-red-600">*</span>
              </Label>
              <Input
                type="date"
                value={weekStartDate}
                onChange={(e) => setWeekStartDate(e.target.value)}
                className={errors.weekStartDate ? "border-red-600" : ""}
              />
              {errors.weekStartDate && (
                <p className="text-xs text-red-600">{errors.weekStartDate}</p>
              )}
            </div>
          )}

          {/* ステータス表示（編集モードのみ） */}
          {isEdit && editTarget && (
            <div className="space-y-1">
              <Label className="text-sm font-medium">ステータス</Label>
              <div className="flex items-center gap-2">
                <StatusBadge status={editTarget.status} />
                {/* PUBLISHED の場合は変更時の警告を表示する */}
                {editTarget.status === "PUBLISHED" && (
                  <span className="flex items-center gap-1 text-xs text-amber-600">
                    <AlertTriangle className="h-3 w-3" />
                    変更すると下書きに戻ります
                  </span>
                )}
              </div>
            </div>
          )}

          {/* シフト割当テーブル（月〜日の7曜日） */}
          <div className="space-y-1">
            <Label className="text-sm font-medium">シフト割当</Label>
            {errors.assignments && (
              <p className="text-xs text-red-600">{errors.assignments}</p>
            )}
            <div className="rounded-md border">
              {DAYS.map(({ key, label }) => (
                <div
                  key={key}
                  className="flex items-center border-b last:border-b-0"
                >
                  {/* 曜日ラベル */}
                  <div className="w-10 shrink-0 px-3 py-2 text-sm font-semibold">
                    {label}
                  </div>
                  {/* パターン選択（ACTIVEパターン + 「休み」） */}
                  <div className="flex-1 px-2 py-1.5">
                    <Select
                      value={assignments[key]}
                      onValueChange={(val) =>
                        setAssignments((prev) => ({ ...prev, [key]: val }))
                      }
                    >
                      <SelectTrigger className="h-9">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={REST_VALUE}>-- 休み --</SelectItem>
                        {patterns.map((p) => (
                          <SelectItem key={p.patternId} value={p.patternId}>
                            {p.name}（{p.startTime}-{p.endTime}）
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </Modal>

      {/* 公開確認ダイアログ */}
      <ConfirmDialog
        open={publishConfirmOpen}
        onClose={() => setPublishConfirmOpen(false)}
        onConfirm={handlePublish}
        title="スケジュール公開"
        description="このスケジュールを公開しますか？対象従業員に通知されます。"
        confirmLabel="公開する"
        loading={publishing}
      />
    </>
  );
}
