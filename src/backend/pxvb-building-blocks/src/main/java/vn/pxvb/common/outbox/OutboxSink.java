package vn.pxvb.common.outbox;

/** Đích nhận sự kiện outbox (RabbitMQ/STOMP/ApplicationEvent ở các epic sau). Phải idempotent. */
public interface OutboxSink {

    void handle(String type, String payloadJson);
}
