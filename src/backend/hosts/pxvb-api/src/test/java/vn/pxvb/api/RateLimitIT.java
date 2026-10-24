package vn.pxvb.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Giới hạn tần suất đăng nhập/PIN/kích hoạt thiết bị (FR-CC-004, L04, PXVB-CC-0429). Ngưỡng hạ xuống 3 để kiểm
 * thử nhanh; các IT khác dùng ngưỡng cao trong {@link AbstractIntegrationTest}.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "pxvb.security.rate-limit-max-requests=3")
class RateLimitIT extends AbstractIntegrationTest {

    private static final String LOGIN = "/api/v1/identity/auth/login";
    private static final String PIN_LOGIN = "/api/v1/identity/auth/pin-login";
    private static final String ACTIVATE = "/api/v1/identity/devices/activate";
    private static final String LOGIN_BODY = "{\"username\":\"nobody\",\"password\":\"wrong\"}";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    @Test
    void loginIsBlockedAfterLimitWithRetryAfter() throws Exception {
        String ip = "10.10.0.1";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post(LOGIN).with(from(ip)).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post(LOGIN).with(from(ip)).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("PXVB-CC-0429"));
    }

    @Test
    void limitIsKeyedByClientAddressSoOtherClientsAreNotAffected() throws Exception {
        for (int i = 0; i < 4; i++) {
            mvc.perform(post(LOGIN).with(from("10.10.0.2")).contentType(MediaType.APPLICATION_JSON)
                    .content(LOGIN_BODY));
        }

        mvc.perform(post(LOGIN).with(from("10.10.0.3")).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pinLoginIsLimitedWithItsOwnCounter() throws Exception {
        String ip = "10.10.0.4";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post(PIN_LOGIN).with(from(ip)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"identifier\":\"X1\",\"pin\":\"000000\"}"));
        }

        mvc.perform(post(PIN_LOGIN).with(from(ip)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"X1\",\"pin\":\"000000\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void deviceActivationIsLimitedPerClient() throws Exception {
        String ip = "10.10.0.5";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post(ACTIVATE).with(from(ip)).contentType(MediaType.APPLICATION_JSON).content("{}"));
        }

        mvc.perform(post(ACTIVATE).with(from(ip)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PXVB-CC-0429"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
