package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.shift.ShiftPattern;
import com.example.kintai.attendance.domain.repository.ShiftPatternRepository;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.example.kintai.shared.kernel.contract.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * シフトパターン再有効化ユースケース（UC-SH-003）— シフトパターンを再有効化する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>パターンをリポジトリから取得する（存在しなければ例外）</li>
 *   <li>集約のreactivate()を呼び出す（INACTIVEであることをガード）</li>
 *   <li>リポジトリに保存する</li>
 * </ol>
 * </p>
 */
@Service
@Transactional
public class ReactivatePatternUseCase implements UseCase<ReactivatePatternCommand, Void> {

    private static final Logger log = LoggerFactory.getLogger(ReactivatePatternUseCase.class);

    /** シフトパターンリポジトリ — パターンのCRUD操作 */
    private final ShiftPatternRepository shiftPatternRepository;

    public ReactivatePatternUseCase(ShiftPatternRepository shiftPatternRepository) {
        this.shiftPatternRepository = shiftPatternRepository;
    }

    /**
     * シフトパターン再有効化を実行する
     *
     * @param command シフトパターン再有効化コマンド（パターンID）
     * @return null（戻り値なし）
     * @throws IllegalArgumentException パターンが見つからない場合
     * @throws IllegalStateException    既にACTIVEの場合
     */
    @Override
    public Void execute(ReactivatePatternCommand command) {
        // パターンをリポジトリから取得する（存在しなければ例外）
        ShiftPattern pattern = findPatternOrThrow(command.patternId());
        log.debug("シフトパターン再有効化: patternId={}, name={}",
                command.patternId().value(), pattern.getName().value());

        // 集約のreactivate()を呼び出す（ガード: INACTIVEであること）
        pattern.reactivate();

        // リポジトリに保存する（楽観的ロックでバージョン管理）
        shiftPatternRepository.save(pattern);

        log.debug("シフトパターン再有効化完了: patternId={}", command.patternId().value());
        return null;
    }

    /**
     * シフトパターンをIDで取得する（見つからない場合は例外をスロー）
     */
    private ShiftPattern findPatternOrThrow(ShiftPatternId patternId) {
        return shiftPatternRepository.findById(patternId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "シフトパターンが見つかりません: " + patternId.value()));
    }
}
