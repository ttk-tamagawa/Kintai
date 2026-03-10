"use client";

import type { ReactNode } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";

// ========================================
// Form 部品コンポーネント群
// TextInput, Select, DatePicker, TimePicker, Checkbox, NumberInput
// ========================================

/** フィールド共通の外枠コンポーネント */
interface FieldWrapperProps {
  /** ラベルテキスト */
  label: string;
  /** フィールドID */
  htmlFor?: string;
  /** 必須かどうか */
  required?: boolean;
  /** エラーメッセージ */
  error?: string;
  /** 追加クラス */
  className?: string;
  /** フィールド要素 */
  children: ReactNode;
}

export function FieldWrapper({
  label,
  htmlFor,
  required,
  error,
  className,
  children,
}: FieldWrapperProps) {
  return (
    <div className={cn("space-y-1.5", className)}>
      <Label htmlFor={htmlFor}>
        {label}
        {required && <span className="ml-0.5 text-destructive">*</span>}
      </Label>
      {children}
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  );
}

// ========================================
// TextInput: テキスト入力フィールド
// ========================================

interface TextInputProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  required?: boolean;
  error?: string;
  disabled?: boolean;
  className?: string;
}

export function TextInput({
  label,
  value,
  onChange,
  placeholder,
  required,
  error,
  disabled,
  className,
}: TextInputProps) {
  const id = `field-${label}`;
  return (
    <FieldWrapper
      label={label}
      htmlFor={id}
      required={required}
      error={error}
      className={className}
    >
      <Input
        id={id}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        disabled={disabled}
      />
    </FieldWrapper>
  );
}

// ========================================
// NumberInput: 数値入力フィールド
// ========================================

interface NumberInputProps {
  label: string;
  value: number | "";
  onChange: (value: number | "") => void;
  min?: number;
  max?: number;
  required?: boolean;
  error?: string;
  disabled?: boolean;
  className?: string;
}

export function NumberInput({
  label,
  value,
  onChange,
  min,
  max,
  required,
  error,
  disabled,
  className,
}: NumberInputProps) {
  const id = `field-${label}`;
  return (
    <FieldWrapper
      label={label}
      htmlFor={id}
      required={required}
      error={error}
      className={className}
    >
      <Input
        id={id}
        type="number"
        value={value}
        onChange={(e) => {
          const v = e.target.value;
          onChange(v === "" ? "" : Number(v));
        }}
        min={min}
        max={max}
        disabled={disabled}
      />
    </FieldWrapper>
  );
}

// ========================================
// DateInput: 日付入力フィールド
// ========================================

interface DateInputProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  error?: string;
  disabled?: boolean;
  className?: string;
}

export function DateInput({
  label,
  value,
  onChange,
  required,
  error,
  disabled,
  className,
}: DateInputProps) {
  const id = `field-${label}`;
  return (
    <FieldWrapper
      label={label}
      htmlFor={id}
      required={required}
      error={error}
      className={className}
    >
      <Input
        id={id}
        type="date"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
      />
    </FieldWrapper>
  );
}

// ========================================
// TimeInput: 時刻入力フィールド
// ========================================

interface TimeInputProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  error?: string;
  disabled?: boolean;
  className?: string;
}

export function TimeInput({
  label,
  value,
  onChange,
  required,
  error,
  disabled,
  className,
}: TimeInputProps) {
  const id = `field-${label}`;
  return (
    <FieldWrapper
      label={label}
      htmlFor={id}
      required={required}
      error={error}
      className={className}
    >
      <Input
        id={id}
        type="time"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
      />
    </FieldWrapper>
  );
}

// ========================================
// SelectField: セレクトボックス
// ========================================

interface SelectOption {
  value: string;
  label: string;
}

interface SelectFieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: SelectOption[];
  placeholder?: string;
  required?: boolean;
  error?: string;
  disabled?: boolean;
  className?: string;
}

export function SelectField({
  label,
  value,
  onChange,
  options,
  placeholder = "選択してください",
  required,
  error,
  disabled,
  className,
}: SelectFieldProps) {
  return (
    <FieldWrapper
      label={label}
      required={required}
      error={error}
      className={className}
    >
      <Select value={value} onValueChange={onChange} disabled={disabled}>
        <SelectTrigger>
          <SelectValue placeholder={placeholder} />
        </SelectTrigger>
        <SelectContent>
          {options.map((opt) => (
            <SelectItem key={opt.value} value={opt.value}>
              {opt.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </FieldWrapper>
  );
}

// ========================================
// CheckboxField: チェックボックス
// ========================================

interface CheckboxFieldProps {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  className?: string;
}

export function CheckboxField({
  label,
  checked,
  onChange,
  disabled,
  className,
}: CheckboxFieldProps) {
  const id = `field-${label}`;
  return (
    <div className={cn("flex items-center gap-2", className)}>
      <Checkbox
        id={id}
        checked={checked}
        onCheckedChange={(v) => onChange(v === true)}
        disabled={disabled}
      />
      <Label htmlFor={id} className="cursor-pointer">
        {label}
      </Label>
    </div>
  );
}
