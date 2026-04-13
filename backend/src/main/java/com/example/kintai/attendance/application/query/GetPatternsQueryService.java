package com.example.kintai.attendance.application.query;

import com.example.kintai.attendance.domain.repository.ShiftQueryRepository;
import com.example.kintai.attendance.domain.repository.ShiftQueryRepository.PatternSummary;
import com.example.kintai.shared.kernel.contract.QueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * シフトパターン一覧取得クエリサービス（UC-SH-Q01）— シフトパターン一覧を取得する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>isActiveフィルタに基づいてパターン一覧を取得する</li>
 *   <li>パターン名の昇順でソート済みのリストを返す</li>
 * </ol>
 * パターン数は少数（通常50件以下）のため、DBレベルでページネーションは不要。</p>
 */
@Service
@Transactional(readOnly = true)
public class GetPatternsQueryService implements QueryService<GetPatternsQuery, List<PatternSummary>> {

    private static final Logger log = LoggerFactory.getLogger(GetPatternsQueryService.class);

    /** シフトクエリリポジトリ — Read Model参照用 */
    private final ShiftQueryRepository shiftQueryRepository;

    public GetPatternsQueryService(ShiftQueryRepository shiftQueryRepository) {
        this.shiftQueryRepository = shiftQueryRepository;
    }

    /**
     * シフトパターン一覧を取得する
     *
     * @param query パターン一覧取得クエリ（isActiveフィルタ）
     * @return パターン概要のリスト（パターン名昇順）
     */
    @Override
    public List<PatternSummary> execute(GetPatternsQuery query) {
        log.debug("シフトパターン一覧取得: isActive={}", query.isActive());

        // ShiftQueryRepositoryからパターンを取得する（名前昇順ソート済み）
        List<PatternSummary> patterns = shiftQueryRepository.findPatterns(query.isActive());

        log.debug("シフトパターン一覧取得完了: {}件", patterns.size());
        return patterns;
    }
}
