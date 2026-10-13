package vn.pxvb.alerting.infrastructure;

import java.security.Principal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import vn.pxvb.common.security.PxvbPrincipal;
import vn.pxvb.identity.contract.AccessTokenVerifier;

/**
 * WebSocket + STOMP (SDD §2.2/§4.6): endpoint {@code /ws}, broker đơn giản trong tiến trình (chuyển sang relay
 * RabbitMQ khi triển khai nhiều nút — L08). Xác thực ở STOMP CONNECT bằng header {@code Authorization: Bearer};
 * chỉ cho phép SUBSCRIBE vào {@code /user/queue/**} (mỗi người chỉ nhận dữ liệu của mình).
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AccessTokenVerifier verifier;
    private final String[] allowedOrigins;

    public WebSocketConfig(AccessTokenVerifier verifier,
                           @Value("${pxvb.websocket.allowed-origins:*}") String[] allowedOrigins) {
        this.verifier = verifier;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) {
                    return message;
                }
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String header = accessor.getFirstNativeHeader("Authorization");
                    if (header == null || !header.startsWith("Bearer ")) {
                        throw new MessagingException("Chưa xác thực");
                    }
                    PxvbPrincipal principal = verifier.verify(header.substring("Bearer ".length()).trim())
                            .orElseThrow(() -> new MessagingException("Token không hợp lệ hoặc đã hết hạn"));
                    Principal user = new UsernamePasswordAuthenticationToken(principal.userId().toString(), null,
                            List.of());
                    accessor.setUser(user);
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    if (destination == null || !destination.startsWith("/user/queue/")) {
                        throw new MessagingException("Không được phép đăng ký kênh này");
                    }
                } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                    throw new MessagingException("Client không được gửi thông điệp");
                }
                return message;
            }
        });
    }
}
