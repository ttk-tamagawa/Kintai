"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { Modal } from "@/components/ui/modal";
import { useToast } from "@/hooks/useToast";
import { createPattern } from "./api";

// ========================================
// シフトパターン登録モーダル（SCR-SHF-002）
// 5フィールドフォーム + クライアントバリデーション + 登録API
// ========================================

interface ShiftPatternModalProps {
  /** モーダル表示状態 */
  open: boolean;
  /** モーダルを閉じるハンドラー */
  onClose: () => void;
  /** 登録完了時のコールバック */
  onCreated: () => void;
}

/** フォーム入力値 */
interface FormValues {
  name: string;
  startTime: string;
  endTime: string;
  breakMinutes: number;
  isOvernight: boolean;
}

/** フィールドエラー */
interface FieldErrors {
  name?: string;
  startTime?: string;
  endTime?: string;
  breakMinutes?: string;
}

/** フォーム初期値 */
const INITIAL_VALUES: FormValues = {
  name: "",
  startTime: "",
  endTime: "",
  breakMinutes: 60,
  isOvernight: false,
};

export function ShiftPatternModal({
  open,
  onClose,
  onCreated,
}: ShiftPatternModalProps) {
  const toast = useToast();
  const [form, setForm] = useState<FormValues>(INITIAL_VALUES);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [submitting, setSubmitting] = useState(false);

  // フォームをリセットする
  const resetForm = () => {
    setForm(INITIAL_VALUES);
    setErrors({});
  };

  // モーダルを閉じる（入力内容を破棄）
  const handleClose = () => {
    resetForm();
    onClose();
  };

  // ========================================
  // クライアントバリデーション
  // ========================================
  const validate = (): boolean => {
    const errs: FieldErrors = {};

    // パターン名: 必須 + 2-20文字
    if (!form.name.trim()) {
      errs.name = "パターン名を入力してください";
    } else if (form.name.trim().length < 2 || form.name.trim().length > 20) {
      errs.name = "2〜20文字で入力してください";
    }

    // 開始時刻: 必須
    if (!form.startTime) {
      errs.startTime = "勤務開始時刻を入力してください";
    }

    // 終了時刻: 必須
    if (!form.endTime) {
      errs.endTime = "勤務終了時刻を入力してください";
    }

    // 休憩時間: 0-120分
    if (form.breakMinutes < 0 || form.breakMinutes > 120) {
      errs.breakMinutes = "0〜120分の範囲で入力してください";
    }

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  // ========================================
  // 登録実行
  // ========================================
  const handleSubmit = async () => {
    if (!validate()) return;
    setSubmitting(true);
    try {
      await createPattern({
        name: form.name.trim(),
        startTime: form.startTime,
        endTime: form.endTime,
        breakMinutes: form.breakMinutes,
        isOvernight: form.isOvernight,
      });
      toast.success(`シフトパターン「${form.name.trim()}」を登録しました`);
      resetForm();
      onCreated();
    } catch (err: unknown) {
      // 409: 同名パターンが既に存在する場合はフィールドエラーで表示
      const error = err as { response?: { status?: number } };
      if (error?.response?.status === 409) {
        setErrors({ name: "同名のパターンが既に存在します" });
      } else {
        toast.apiError(err);
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title="シフトパターン登録"
      className="sm:max-w-[480px]"
      footer={
        <>
          <Button variant="outline" onClick={handleClose} disabled={submitting}>
            キャンセル
          </Button>
          <Button onClick={handleSubmit} disabled={submitting}>
            {submitting ? "登録中..." : "登録"}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {/* パターン名 */}
        <div className="space-y-1">
          <Label className="text-sm font-medium">
            パターン名 <span className="text-red-600">*</span>
          </Label>
          <Input
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            placeholder="例: 早番"
            className={errors.name ? "border-red-600" : ""}
          />
          {errors.name && (
            <p className="text-xs text-red-600">{errors.name}</p>
          )}
        </div>

        {/* 勤務開始時刻・終了時刻（横並び） */}
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1">
            <Label className="text-sm font-medium">
              勤務開始時刻 <span className="text-red-600">*</span>
            </Label>
            <Input
              type="time"
              value={form.startTime}
              onChange={(e) => setForm({ ...form, startTime: e.target.value })}
              className={errors.startTime ? "border-red-600" : ""}
            />
            {errors.startTime && (
              <p className="text-xs text-red-600">{errors.startTime}</p>
            )}
          </div>
          <div className="space-y-1">
            <Label className="text-sm font-medium">
              勤務終了時刻 <span className="text-red-600">*</span>
            </Label>
            <Input
              type="time"
              value={form.endTime}
              onChange={(e) => setForm({ ...form, endTime: e.target.value })}
              className={errors.endTime ? "border-red-600" : ""}
            />
            {errors.endTime && (
              <p className="text-xs text-red-600">{errors.endTime}</p>
            )}
          </div>
        </div>

        {/* 休憩時間（分） */}
        <div className="space-y-1">
          <Label className="text-sm font-medium">
            休憩時間（分）<span className="text-red-600">*</span>
          </Label>
          <Input
            type="number"
            min={0}
            max={120}
            value={form.breakMinutes}
            onChange={(e) =>
              setForm({ ...form, breakMinutes: Number(e.target.value) })
            }
            className={errors.breakMinutes ? "border-red-600" : ""}
          />
          {errors.breakMinutes && (
            <p className="text-xs text-red-600">{errors.breakMinutes}</p>
          )}
        </div>

        {/* 日跨ぎ（夜勤）チェックボックス */}
        <div className="flex items-center gap-2">
          <Checkbox
            id="isOvernight"
            checked={form.isOvernight}
            onCheckedChange={(checked) =>
              setForm({ ...form, isOvernight: checked === true })
            }
          />
          <Label htmlFor="isOvernight" className="text-sm font-medium">
            日跨ぎ（夜勤）
          </Label>
        </div>
      </div>
    </Modal>
  );
}
