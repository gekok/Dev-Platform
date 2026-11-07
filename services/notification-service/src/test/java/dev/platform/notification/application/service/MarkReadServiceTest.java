package dev.platform.notification.application.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.platform.notification.application.command.MarkReadCommand;
import dev.platform.notification.application.port.out.SaveNotificationPort;
import dev.platform.notification.domain.aggregate.NotificationAggregate;

class MarkReadServiceTest {

    private SaveNotificationPort notificationRepository;
    private MarkReadService service;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(SaveNotificationPort.class);
        service = new MarkReadService(notificationRepository);
    }

    @Test
    void markReadUpdatesNotificationOwnedByUser() {
        UUID userId = UUID.randomUUID();
        NotificationAggregate notification = notification(1L, userId, false);

        when(notificationRepository.findById(1L))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification))
                .thenReturn(notification);

        NotificationAggregate result = service.markRead(
                new MarkReadCommand(1L, userId));

        assertSame(notification, result);
        assertTrue(result.isRead());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markReadKeepsNotificationReadWhenRequestIsRepeated() {
        UUID userId = UUID.randomUUID();
        NotificationAggregate notification = notification(1L, userId, false);

        when(notificationRepository.findById(1L))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification))
                .thenReturn(notification);

        NotificationAggregate first = service.markRead(
                new MarkReadCommand(1L, userId));
        NotificationAggregate second = service.markRead(
                new MarkReadCommand(1L, userId));

        assertTrue(first.isRead());
        assertTrue(second.isRead());
        verify(notificationRepository, times(2)).save(notification);
    }

    @Test
    void markReadRejectsDifferentUser() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        NotificationAggregate notification = notification(1L, ownerId, false);

        when(notificationRepository.findById(1L))
                .thenReturn(Optional.of(notification));

        assertThrows(
                SecurityException.class,
                () -> service.markRead(new MarkReadCommand(1L, otherUserId)));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markReadRejectsUnknownNotification() {
        UUID userId = UUID.randomUUID();

        when(notificationRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                NoSuchElementException.class,
                () -> service.markRead(new MarkReadCommand(999L, userId)));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markReadRejectsNullCommand() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.markRead(null));

        verifyNoInteractions(notificationRepository);
    }

    private NotificationAggregate notification(Long id, UUID userId, boolean read) {
        NotificationAggregate notification = new NotificationAggregate();
        notification.setId(id);
        notification.setUserId(userId);
        notification.setRead(read);
        return notification;
    }
}
