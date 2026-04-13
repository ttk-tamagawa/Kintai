package com.example.kintai.shared.kernel.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AggregateRoot 基底クラスの単体テスト
 *
 * <p>registerEvent / getDomainEvents / clearDomainEvents のライフサイクルを検証する。</p>
 */
@DisplayName("AggregateRoot 基底クラス テスト")
class AggregateRootTest {

    /**
     * テスト用の具象AggregateRoot — registerEvent() を公開する
     */
    private static class TestAggregate extends AggregateRoot {
        void addEvent(DomainEvent event) {
            registerEvent(event);
        }
    }

    /**
     * テスト用の具象DomainEvent
     */
    private static class TestEvent extends DomainEvent {
        @Override
        public String getEventType() {
            return "TEST_EVENT";
        }
    }

    @Test
    @DisplayName("registerEvent → getDomainEvents で登録したイベントが取得できる")
    void registerEvent_canBeRetrievedByGetDomainEvents() {
        // 集約を生成する
        TestAggregate aggregate = new TestAggregate();

        // イベントを登録する
        TestEvent event = new TestEvent();
        aggregate.addEvent(event);

        // getDomainEvents() で取得できることを検証する
        assertEquals(1, aggregate.getDomainEvents().size(),
                "登録したイベントが1件取得できるべき");
        assertSame(event, aggregate.getDomainEvents().getFirst(),
                "登録したイベントと同一インスタンスであるべき");
    }

    @Test
    @DisplayName("clearDomainEvents でイベントリストが空になる")
    void clearDomainEvents_emptiesTheList() {
        // 集約にイベントを登録する
        TestAggregate aggregate = new TestAggregate();
        aggregate.addEvent(new TestEvent());
        aggregate.addEvent(new TestEvent());
        assertEquals(2, aggregate.getDomainEvents().size());

        // クリアする
        aggregate.clearDomainEvents();

        // 空になったことを検証する
        assertTrue(aggregate.getDomainEvents().isEmpty(),
                "clearDomainEvents後はイベントリストが空であるべき");
    }

    @Test
    @DisplayName("registerEvent(null) で IllegalArgumentException がスローされる")
    void registerEvent_null_throwsException() {
        TestAggregate aggregate = new TestAggregate();

        // null を登録すると例外がスローされることを検証する
        assertThrows(IllegalArgumentException.class, () -> aggregate.addEvent(null),
                "null のイベント登録は拒否されるべき");
    }

    @Test
    @DisplayName("getDomainEvents は変更不可リストを返す")
    void getDomainEvents_returnsUnmodifiableList() {
        TestAggregate aggregate = new TestAggregate();
        aggregate.addEvent(new TestEvent());

        // 返却リストへの追加操作が拒否されることを検証する
        assertThrows(UnsupportedOperationException.class,
                () -> aggregate.getDomainEvents().add(new TestEvent()),
                "getDomainEvents() の戻り値は変更不可であるべき");
    }

    @Test
    @DisplayName("複数イベントが登録順で取得できる")
    void multipleEvents_areRetrievedInOrder() {
        TestAggregate aggregate = new TestAggregate();

        // 3つのイベントを順に登録する
        TestEvent event1 = new TestEvent();
        TestEvent event2 = new TestEvent();
        TestEvent event3 = new TestEvent();
        aggregate.addEvent(event1);
        aggregate.addEvent(event2);
        aggregate.addEvent(event3);

        // 登録順で取得できることを検証する
        assertEquals(3, aggregate.getDomainEvents().size(),
                "3件のイベントが取得できるべき");
        assertSame(event1, aggregate.getDomainEvents().get(0));
        assertSame(event2, aggregate.getDomainEvents().get(1));
        assertSame(event3, aggregate.getDomainEvents().get(2));
    }

    @Test
    @DisplayName("初期状態ではイベントリストが空である")
    void initialState_hasEmptyEvents() {
        TestAggregate aggregate = new TestAggregate();

        // 初期状態で空であることを検証する
        assertTrue(aggregate.getDomainEvents().isEmpty(),
                "初期状態ではイベントリストが空であるべき");
    }
}
