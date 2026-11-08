package dev.platform.contracts.event;

public record ProjectStatusChanged(Long projectId, String previousStatus, String status) {
}
