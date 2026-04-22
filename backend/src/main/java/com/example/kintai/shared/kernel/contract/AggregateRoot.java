package com.example.kintai.shared.kernel.contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 集約ルート基底クラス — ドメインイベントの登録・取得・クリア機構を提供する
 *
 * <p>集約（AttendanceRecord, WeeklySchedule, ShiftPattern）がこのクラスを継承することで、
 * コマンドメソッド内で発生したドメインイベントを集約自身が保持できるようになる。</p>
 *
 * <p>典型的な使用フロー:
 * <ol>
 *   <li>集約のコマンドメソッド内で {@link #registerEvent(DomainEvent)} を呼び、イベントを登録する</li>
 *   <li>UseCase が {@link #getDomainEvents()} で蓄積されたイベントを取得し、永続化・発行する</li>
 *   <li>UseCase が {@link #clearDomainEvents()} でイベントリストをクリアする</li>
 * </ol>
 * </p>
 *
 * <p>使用例:
 * <pre>{@code
 * public class AttendanceRecord extends AggregateRoot {
 *     public void clockIn(ClockTime clockTime, ShiftPatternId shiftPatternId) {
 *         // ... 状態遷移ロジック ...
 *         registerEvent(ClockedInEvent.of(this.id, this.employeeId, clockTime));
 *     }
 * }
 * }</pre>
 * </p>
 */
public abstract class AggregateRoot {

    /** 集約内で発生したドメインイベントのリスト — registerEvent() で追加される */
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * ドメインイベントを登録する — 集約のコマンドメソッド内から呼び出す
     *
     * <p>状態遷移が成功した直後に呼び出すことで、
     * 状態変更とイベント発行の一貫性を保証する。</p>
     *
     * @param event 登録するドメインイベント
     * @throws IllegalArgumentException event が null の場合
     */
    protected void registerEvent(DomainEvent event) {
        // null のイベント登録を防止する
        if (event == null) {
            throw new IllegalArgumentException("ドメインイベントは null にできません");
        }
        domainEvents.add(event);
    }

    /**
     * 蓄積されたドメインイベントを取得する — UseCase がイベント永続化時に使用する
     *
     * <p>返却されるリストは変更不可のコピーであり、
     * 内部リストへの外部からの変更を防止する。</p>
     *
     * @return 登録済みドメインイベントの変更不可リスト
     */
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * ドメインイベントリストをクリアする — UseCase がイベント永続化完了後に呼び出す
     *
     * <p>イベントの二重永続化を防ぐため、永続化が完了したら必ず呼び出すこと。</p>
     */
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    /**
     * 別の集約からドメインイベントを引き継ぐ — リポジトリ実装の {@code save()} で使用する
     *
     * <p>リポジトリの {@code save()} が {@code reconstruct()} で新しいインスタンスを返す際、
     * 元の集約に登録されていたイベントが失われないよう、再構築後のインスタンスに
     * 本メソッドでイベントを引き継がせる。</p>
     *
     * <p>典型的な使用例:
     * <pre>{@code
     * XxxRepositoryImpl#save(Xxx aggregate) {
     *     jpaRepo.saveAndFlush(toEntity(aggregate));
     *     Xxx reconstructed = Xxx.reconstruct(...);
     *     reconstructed.inheritEventsFrom(aggregate);  // ← イベントを引き継ぐ
     *     return reconstructed;
     * }
     * }</pre>
     * </p>
     *
     * @param source イベントの引き継ぎ元となる集約（通常は save() の引数）
     * @throws IllegalArgumentException source が null の場合
     */
    public void inheritEventsFrom(AggregateRoot source) {
        if (source == null) {
            throw new IllegalArgumentException("イベント引き継ぎ元の集約は null にできません");
        }
        this.domainEvents.addAll(source.domainEvents);
    }
}
