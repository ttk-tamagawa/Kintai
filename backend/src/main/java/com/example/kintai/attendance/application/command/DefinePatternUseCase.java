package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.shift.PatternName;
import com.example.kintai.attendance.domain.model.shift.ShiftPattern;
import com.example.kintai.attendance.domain.repository.ShiftPatternRepository;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.example.kintai.shared.kernel.contract.DomainEvent;
import com.example.kintai.shared.kernel.contract.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * シフトパターン定義ユースケース（UC-SH-001）— 新しいシフトパターンを定義する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>パターン名の値オブジェクトを生成する（2〜20文字バリデーション）</li>
 *   <li>パターン名の一意性をリポジトリで検証する（INV-SH-001）</li>
 *   <li>ShiftPattern.define()でドメインオブジェクトを生成する（初期状態: ACTIVE）</li>
 *   <li>リポジトリに保存する</li>
 *   <li>ShiftPatternDefinedEventを発行する</li>
 * </ol>
 * </p>
 *
 * <p>ShiftPatternはイベントソーシングを使用しない（状態遷移が単純なため）。
 * Springイベント発行のみでプロジェクターがRead Modelのパターン名マスタを更新する。</p>
 */
@Service
@Transactional
public class DefinePatternUseCase implements UseCase<DefinePatternCommand, ShiftPatternId> {

    private static final Logger log = LoggerFactory.getLogger(DefinePatternUseCase.class);

    /** シフトパターンリポジトリ — パターンのCRUD操作 */
    private final ShiftPatternRepository shiftPatternRepository;

    /** Springイベント発行 — プロジェクターがRead Modelを更新するトリガー */
    private final ApplicationEventPublisher eventPublisher;

    public DefinePatternUseCase(
            ShiftPatternRepository shiftPatternRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.shiftPatternRepository = shiftPatternRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * シフトパターン定義を実行する
     *
     * @param command シフトパターン定義コマンド（パターン名、開始/終了時刻、休憩時間、夜勤フラグ）
     * @return 作成されたシフトパターンID
     * @throws IllegalArgumentException パターン名が重複している場合
     */
    @Override
    public ShiftPatternId execute(DefinePatternCommand command) {
        // パターン名の値オブジェクトを生成する（2〜20文字のバリデーションが実行される）
        PatternName patternName = new PatternName(command.name());
        log.debug("シフトパターン定義: name={}", patternName.value());

        // パターン名の一意性をリポジトリで検証する（INV-SH-001: 名前はシステム全体で一意）
        if (shiftPatternRepository.existsByName(patternName)) {
            throw new IllegalArgumentException(
                    "パターン名「" + patternName.value() + "」は既に使用されています"
            );
        }

        // ShiftPattern.define()でドメインオブジェクトを生成する（初期状態: ACTIVE、version=0）
        ShiftPattern pattern = ShiftPattern.define(
                patternName, command.startTime(), command.endTime(),
                command.breakMinutes(), command.isOvernight()
        );

        // リポジトリに保存する（JPAが自動的にpersistを実行）
        ShiftPattern saved = shiftPatternRepository.save(pattern);

        // 集約に蓄積されたドメインイベントを一括で発行する（ShiftPatternはイベントストアを使用しない）
        for (DomainEvent event : saved.getDomainEvents()) {
            eventPublisher.publishEvent(event);
        }
        saved.clearDomainEvents();

        log.debug("シフトパターン定義完了: patternId={}", saved.getId().value());
        return saved.getId();
    }
}
