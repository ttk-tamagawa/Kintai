package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.shift.PatternName;
import com.example.kintai.attendance.domain.model.shift.ShiftPattern;
import com.example.kintai.attendance.domain.repository.ShiftPatternRepository;
import com.example.kintai.attendance.domain.repository.WeeklyScheduleEventRepository;
import com.example.kintai.attendance.domain.repository.WeeklyScheduleRepository;
import com.example.kintai.shared.domain.exception.BusinessRuleViolationException;
import com.example.kintai.shared.domain.exception.ResourceNotFoundException;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * WeeklyScheduleUseCaseSupport のユニットテスト
 *
 * <p>validateAndCollectPatternNames の検証と N+1 回避を確認する。
 * 依存は Mockito でモック化し、純粋なユニットテストとして実行する。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WeeklyScheduleUseCaseSupport テスト")
class WeeklyScheduleUseCaseSupportTest {

    @Mock
    private WeeklyScheduleRepository weeklyScheduleRepository;

    @Mock
    private WeeklyScheduleEventRepository weeklyScheduleEventRepository;

    @Mock
    private ShiftPatternRepository shiftPatternRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ObjectMapper objectMapper;

    private WeeklyScheduleUseCaseSupport support;

    @BeforeEach
    void setUp() {
        support = new WeeklyScheduleUseCaseSupport(
                weeklyScheduleRepository,
                weeklyScheduleEventRepository,
                shiftPatternRepository,
                eventPublisher,
                objectMapper
        );
    }

    // ========================================
    // ヘルパー: ACTIVE / INACTIVE のパターンを生成する
    // ========================================

    /** 指定IDの ACTIVE パターンを生成する（テスト用） */
    private ShiftPattern activePattern(ShiftPatternId id, String name) {
        Instant now = Instant.now();
        return ShiftPattern.reconstruct(
                id, new PatternName(name),
                LocalTime.of(9, 0), LocalTime.of(18, 0),
                60, false, true, 0, now, now
        );
    }

    /** 指定IDの INACTIVE パターンを生成する（テスト用） */
    private ShiftPattern inactivePattern(ShiftPatternId id, String name) {
        Instant now = Instant.now();
        return ShiftPattern.reconstruct(
                id, new PatternName(name),
                LocalTime.of(9, 0), LocalTime.of(18, 0),
                60, false, false, 0, now, now
        );
    }

    @Nested
    @DisplayName("validateAndCollectPatternNames")
    class ValidateAndCollectPatternNames {

        @Test
        @DisplayName("正常系: 全パターンがACTIVEならパターン名マップを返す")
        void returnsPatternNameMapWhenAllActive() {
            // 月・水・金に別々のパターンを割り当てる
            ShiftPatternId id1 = ShiftPatternId.of(UUID.randomUUID());
            ShiftPatternId id2 = ShiftPatternId.of(UUID.randomUUID());
            ShiftPatternId id3 = ShiftPatternId.of(UUID.randomUUID());

            Map<DayOfWeek, ShiftPatternId> assignments = new EnumMap<>(DayOfWeek.class);
            assignments.put(DayOfWeek.MONDAY, id1);
            assignments.put(DayOfWeek.WEDNESDAY, id2);
            assignments.put(DayOfWeek.FRIDAY, id3);

            when(shiftPatternRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(
                            activePattern(id1, "日勤"),
                            activePattern(id2, "早番"),
                            activePattern(id3, "遅番")
                    ));

            Map<ShiftPatternId, String> result = support.validateAndCollectPatternNames(assignments);

            assertEquals(3, result.size());
            assertEquals("日勤", result.get(id1));
            assertEquals("早番", result.get(id2));
            assertEquals("遅番", result.get(id3));
        }

        @Test
        @DisplayName("N+1回避: findAllByIdが1回しか呼ばれない")
        void callsFindAllByIdExactlyOnce() {
            ShiftPatternId id1 = ShiftPatternId.of(UUID.randomUUID());
            ShiftPatternId id2 = ShiftPatternId.of(UUID.randomUUID());

            Map<DayOfWeek, ShiftPatternId> assignments = new EnumMap<>(DayOfWeek.class);
            assignments.put(DayOfWeek.MONDAY, id1);
            assignments.put(DayOfWeek.TUESDAY, id2);

            when(shiftPatternRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(
                            activePattern(id1, "パターンA"),
                            activePattern(id2, "パターンB")
                    ));

            support.validateAndCollectPatternNames(assignments);

            // 一括取得が1回のみで、個別のfindByIdが呼ばれていないことを検証する
            verify(shiftPatternRepository, times(1)).findAllById(anyCollection());
            verify(shiftPatternRepository, never()).findById(any(ShiftPatternId.class));
        }

        @Test
        @DisplayName("N+1回避: 同じパターンIDが複数曜日に割り当たっていても、findAllByIdには重複排除されたIDが渡される")
        void deduplicatesPatternIdsBeforeBulkFetch() {
            // 月〜金すべてに同じパターンを割り当てる
            ShiftPatternId sameId = ShiftPatternId.of(UUID.randomUUID());

            Map<DayOfWeek, ShiftPatternId> assignments = new EnumMap<>(DayOfWeek.class);
            assignments.put(DayOfWeek.MONDAY, sameId);
            assignments.put(DayOfWeek.TUESDAY, sameId);
            assignments.put(DayOfWeek.WEDNESDAY, sameId);
            assignments.put(DayOfWeek.THURSDAY, sameId);
            assignments.put(DayOfWeek.FRIDAY, sameId);

            when(shiftPatternRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(activePattern(sameId, "通常")));

            support.validateAndCollectPatternNames(assignments);

            // findAllById に渡された引数のIDが1個（重複排除済み）であることを検証する
            ArgumentCaptor<Collection<ShiftPatternId>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(shiftPatternRepository).findAllById(captor.capture());
            assertEquals(1, captor.getValue().size(), "重複排除されたIDセットが渡されること");
        }

        @Test
        @DisplayName("異常系: パターンが見つからない場合、ResourceNotFoundException（メッセージに曜日名含む）")
        void throwsResourceNotFoundWhenPatternMissing() {
            ShiftPatternId foundId = ShiftPatternId.of(UUID.randomUUID());
            ShiftPatternId missingId = ShiftPatternId.of(UUID.randomUUID());

            Map<DayOfWeek, ShiftPatternId> assignments = new EnumMap<>(DayOfWeek.class);
            assignments.put(DayOfWeek.MONDAY, foundId);
            assignments.put(DayOfWeek.TUESDAY, missingId);

            // missingId は返ってこない（DBに存在しない想定）
            when(shiftPatternRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(activePattern(foundId, "日勤")));

            ResourceNotFoundException ex = assertThrows(
                    ResourceNotFoundException.class,
                    () -> support.validateAndCollectPatternNames(assignments)
            );
            assertTrue(ex.getMessage().contains(missingId.value().toString()),
                    "メッセージに欠損パターンIDが含まれること");
            assertTrue(ex.getMessage().contains("TUESDAY"),
                    "メッセージに曜日名が含まれること");
        }

        @Test
        @DisplayName("異常系: パターンがINACTIVEなら、BusinessRuleViolationException（メッセージにパターン名含む）")
        void throwsBusinessRuleViolationWhenPatternInactive() {
            ShiftPatternId id1 = ShiftPatternId.of(UUID.randomUUID());
            ShiftPatternId id2 = ShiftPatternId.of(UUID.randomUUID());

            Map<DayOfWeek, ShiftPatternId> assignments = new EnumMap<>(DayOfWeek.class);
            assignments.put(DayOfWeek.MONDAY, id1);
            assignments.put(DayOfWeek.FRIDAY, id2);

            when(shiftPatternRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(
                            activePattern(id1, "日勤"),
                            inactivePattern(id2, "廃止済み")
                    ));

            BusinessRuleViolationException ex = assertThrows(
                    BusinessRuleViolationException.class,
                    () -> support.validateAndCollectPatternNames(assignments)
            );
            assertTrue(ex.getMessage().contains("廃止済み"),
                    "メッセージにINACTIVEパターン名が含まれること");
            assertTrue(ex.getMessage().contains("FRIDAY"),
                    "メッセージに曜日名が含まれること");
        }

        @Test
        @DisplayName("空割当: 空のMapを渡すと空のMapが返り、findAllByIdは空コレクションで呼ばれる")
        void returnsEmptyMapForEmptyAssignments() {
            Map<DayOfWeek, ShiftPatternId> assignments = new EnumMap<>(DayOfWeek.class);

            when(shiftPatternRepository.findAllById(anyCollection()))
                    .thenReturn(List.of());

            Map<ShiftPatternId, String> result = support.validateAndCollectPatternNames(assignments);

            assertTrue(result.isEmpty());
        }
    }
}
