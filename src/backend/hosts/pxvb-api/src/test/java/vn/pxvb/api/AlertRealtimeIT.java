package vn.pxvb.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import vn.pxvb.alerting.contract.AlertRaiser;
import vn.pxvb.alerting.contract.AlertRaiser.AlertCommand;

/** Đẩy cảnh báo realtime qua WebSocket/STOMP tới đúng người nhận (FR-CC-053). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AlertRealtimeIT extends AbstractIntegrationTest {

    @LocalServerPort
    int port;
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    AlertRaiser raiser;

    private WebSocketStompClient client() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        return client;
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders headers = new StompHeaders();
        if (token != null) {
            headers.add("Authorization", "Bearer " + token);
        }
        return client().connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() { }).get(10, TimeUnit.SECONDS);
    }

    @Test
    void recipientReceivesAlertOverStompAndOthersDoNot() throws Exception {
        String admin = adminToken(mvc, mapper);
        TestUser leader = createUser(mvc, mapper, admin, "lead", "SHIFT_LEADER");
        TestUser planner = createUser(mvc, mapper, admin, "plan", "PLANNER");

        BlockingQueue<Map<?, ?>> leaderQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Map<?, ?>> plannerQueue = new LinkedBlockingQueue<>();
        StompSession leaderSession = connect(leader.token());
        StompSession plannerSession = connect(planner.token());
        leaderSession.subscribe("/user/queue/alerts", handler(leaderQueue));
        plannerSession.subscribe("/user/queue/alerts", handler(plannerQueue));
        Thread.sleep(500);

        String subject = "M-" + UUID.randomUUID();
        raiser.raise(new AlertCommand("MT_WASTE_HIGH", "Machine", subject, null, null, "Hao hụt cao",
                "Vượt ngưỡng 3%", Map.of(), null, "CRITICAL"));

        Map<?, ?> message = leaderQueue.poll(10, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(message.get("event")).isEqualTo("RAISED");
        assertThat(message.get("ruleCode")).isEqualTo("MT_WASTE_HIGH");
        assertThat(message.get("severity")).isEqualTo("CRITICAL");
        assertThat(plannerQueue.poll(1, TimeUnit.SECONDS)).isNull();

        leaderSession.disconnect();
        plannerSession.disconnect();
    }

    @Test
    void connectWithoutValidTokenIsRejected() throws Exception {
        try {
            StompSession session = connect(null);
            Thread.sleep(500);
            assertThat(session.isConnected()).isFalse();
        } catch (Exception expected) {
            // Máy chủ đóng kết nối ngay → ném lỗi khi kết nối: đúng mong đợi
        }
        try {
            StompSession session = connect("khong.phai.token");
            Thread.sleep(500);
            assertThat(session.isConnected()).isFalse();
        } catch (Exception expected) {
            // như trên
        }
    }

    private StompFrameHandler handler(BlockingQueue<Map<?, ?>> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((Map<?, ?>) payload);
            }
        };
    }
}
