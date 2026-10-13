package vn.pxvb.alerting.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.pxvb.common.outbox.OutboxSink;
import vn.pxvb.identity.contract.IdentityDirectory;

/**
 * Chuyển sự kiện của workflow (qua outbox) thành thông báo trong ứng dụng. Không phụ thuộc module workflow:
 * chỉ đọc JSON theo hợp đồng sự kiện {@code approval.*} / {@code adjustment.*}.
 */
@Component
public class WorkflowEventSink implements OutboxSink {

    private final IdentityDirectory directory;
    private final NotificationService notifications;
    private final ObjectMapper mapper;

    public WorkflowEventSink(IdentityDirectory directory, NotificationService notifications, ObjectMapper mapper) {
        this.directory = directory;
        this.notifications = notifications;
        this.mapper = mapper;
    }

    @Override
    public void handle(String type, String payloadJson) {
        if (!type.startsWith("approval.") && !type.startsWith("adjustment.")) {
            return;
        }
        JsonNode p;
        try {
            p = mapper.readTree(payloadJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("Payload sự kiện không hợp lệ: " + type, e);
        }
        switch (type) {
            case "approval.requested" -> {
                Set<UUID> targets = new LinkedHashSet<>();
                p.path("roleCodes").forEach(r -> targets.addAll(directory.userIdsWithRole(r.asText())));
                p.path("userIds").forEach(u -> targets.add(UUID.fromString(u.asText())));
                targets.remove(UUID.fromString(p.path("startedBy").asText()));
                notifications.notifyUsers(targets, "APPROVAL_REQUESTED",
                        "Có yêu cầu cần duyệt: " + p.path("flowCode").asText(), null,
                        "/approvals/" + p.path("instanceId").asText(), null);
            }
            case "approval.completed" -> notifications.notifyUsers(
                    Set.of(UUID.fromString(p.path("startedBy").asText())), "APPROVAL_COMPLETED",
                    "Yêu cầu " + p.path("flowCode").asText() + " đã " + label(p.path("status").asText()), null,
                    "/approvals/" + p.path("instanceId").asText(), null);
            case "approval.overdue" -> {
                Set<UUID> targets = new LinkedHashSet<>();
                String role = p.path("roleCode").asText();
                if (!role.isEmpty()) {
                    targets.addAll(directory.userIdsWithRole(role));
                }
                targets.add(UUID.fromString(p.path("startedBy").asText()));
                notifications.notifyUsers(targets, "APPROVAL_OVERDUE",
                        "Yêu cầu duyệt quá hạn: " + p.path("flowCode").asText(), null,
                        "/approvals/" + p.path("instanceId").asText(), null);
            }
            case "adjustment.requested" -> notifications.notifyUsers(
                    directory.userIdsWithRole(p.path("approverRoleCode").asText()), "ADJUSTMENT_REQUESTED",
                    "Có yêu cầu điều chỉnh dữ liệu chờ duyệt: " + p.path("code").asText(), null,
                    "/adjustments/" + p.path("requestId").asText(), null);
            case "adjustment.decided" -> notifications.notifyUsers(
                    Set.of(UUID.fromString(p.path("requestedBy").asText())), "ADJUSTMENT_DECIDED",
                    "Yêu cầu điều chỉnh " + p.path("code").asText() + ": " + label(p.path("status").asText()), null,
                    "/adjustments/" + p.path("requestId").asText(), null);
            default -> { }
        }
    }

    private static String label(String status) {
        return switch (status) {
            case "APPROVED" -> "được duyệt";
            case "APPLIED" -> "đã được duyệt và áp dụng";
            case "REJECTED" -> "bị từ chối";
            case "RETURNED" -> "bị trả lại";
            case "CANCELLED" -> "đã hủy";
            default -> status;
        };
    }
}
