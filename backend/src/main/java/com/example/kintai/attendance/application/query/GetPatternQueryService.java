package com.example.kintai.attendance.application.query;

import com.example.kintai.attendance.application.query.ShiftFinder.PatternSummary;
import com.example.kintai.shared.domain.exception.ResourceNotFoundException;
import com.example.kintai.shared.kernel.contract.QueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * シフトパターン詳細取得クエリサービス（UC-SH-Q02）— シフトパターンの詳細を取得する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>パターンIDでShiftFinderから検索する</li>
 *   <li>見つからない場合は例外をスローする</li>
 * </ol>
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class GetPatternQueryService implements QueryService<GetPatternQuery, PatternSummary> {

    private static final Logger log = LoggerFactory.getLogger(GetPatternQueryService.class);

    /** シフトファインダー — Read Model参照用 */
    private final ShiftFinder shiftFinder;

    public GetPatternQueryService(ShiftFinder shiftFinder) {
        this.shiftFinder = shiftFinder;
    }

    /**
     * シフトパターンの詳細を取得する
     *
     * @param query パターン詳細取得クエリ（パターンID）
     * @return パターン概要
     * @throws ResourceNotFoundException パターンが見つからない場合
     */
    @Override
    public PatternSummary execute(GetPatternQuery query) {
        log.debug("シフトパターン詳細取得: patternId={}", query.patternId());

        // ShiftFinderからIDで検索する（見つからなければ例外）
        PatternSummary pattern = shiftFinder.findPatternById(query.patternId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "シフトパターンが見つかりません: " + query.patternId()));

        log.debug("シフトパターン詳細取得完了: name={}", pattern.name());
        return pattern;
    }
}
