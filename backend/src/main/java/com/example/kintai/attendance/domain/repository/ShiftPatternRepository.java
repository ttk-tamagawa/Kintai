package com.example.kintai.attendance.domain.repository;

import com.example.kintai.attendance.domain.model.shift.PatternName;
import com.example.kintai.attendance.domain.model.shift.ShiftPattern;
import com.example.kintai.shared.domain.model.ShiftPatternId;

import java.util.List;
import java.util.Optional;

/**
 * シフトパターンリポジトリ — シフトパターンの永続化インターフェース
 *
 * <p>パターンのCRUD操作と名前の一意性検証を提供する。
 * パターン名はシステム全体で一意（INV-SH-001）。</p>
 */
public interface ShiftPatternRepository {

    /**
     * シフトパターンIDで検索する
     */
    Optional<ShiftPattern> findById(ShiftPatternId id);

    /**
     * 全パターンを取得する（有効/無効フィルタ対応）
     *
     * @param activeOnly trueの場合、有効なパターンのみ返す
     * @return パターン一覧
     */
    List<ShiftPattern> findAll(boolean activeOnly);

    /**
     * シフトパターンを保存する（新規作成または更新）
     *
     * @param pattern 保存するシフトパターン
     * @return バージョン更新済みのシフトパターン
     */
    ShiftPattern save(ShiftPattern pattern);

    /**
     * パターン名が既に存在するか確認する（一意性チェック）
     *
     * @param name パターン名
     * @return 存在する場合true
     */
    boolean existsByName(PatternName name);
}
