package dev.platform.notification.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.platform.notification.application.port.out.SaveNotificationPort;
import dev.platform.notification.domain.aggregate.NotificationAggregate;

class ListMyNotificationsServiceTest {

    private SaveNotificationPort notificationRepository;
    private ListMyNotificationsService service;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(SaveNotificationPort.class);
        service = new ListMyNotificationsService(notificationRepository);
    }

    @Test
    void listMineReturnsAllNotificationsWhenUnreadOnlyIsFalse() {
        UUID userId = UUID.randomUUID();
        List<NotificationAggregate> expected = List.of(
                new NotificationAggregate(),
                new NotificationAggregate());

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(expected);

        List<NotificationAggregate> actual = service.listMine(userId, false);

        assertEquals(expected, actual);
        verify(notificationRepository)
                .findByUserIdOrderByCreatedAtDesc(userId);
        verify(notificationRepository, never())
                .findByUserIdAndReadFalse(userId);
    }

    @Test
    void listMineReturnsUnreadNotificationsWhenUnreadOnlyIsTrue() {
        UUID userId = UUID.randomUUID();
        List<NotificationAggregate> expected = List.of(
                new NotificationAggregate());

        when(notificationRepository.findByUserIdAndReadFalse(userId))
                .thenReturn(expected);

        List<NotificationAggregate> actual = service.listMine(userId, true);

        assertEquals(expected, actual);
        verify(notificationRepository)
                .findByUserIdAndReadFalse(userId);
        verify(notificationRepository, never())
                .findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    void listMineRejectsNullUserId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.listMine(null, false));

        verifyNoInteractions(notificationRepository);
    }
}
