package vn.pxvb.identity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.core.Authentication;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.security.PxvbPrincipal;
import vn.pxvb.identity.application.IdentityProperties;
import vn.pxvb.identity.application.TokenService;

/** Bảo mật stateless: deny-by-default, JWT access token, Argon2id (FR-CC-001/004, NFR ATTT §16.3). */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(IdentityProperties.class)
public class SecurityConfig {

    static final String MUST_CHANGE_PASSWORD = "PXVB_MUST_CHANGE_PASSWORD";

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TokenService tokens, ObjectMapper mapper)
            throws Exception {
        AuthenticationEntryPoint entryPoint = (req, res, ex) -> write(res, mapper, ErrorCode.UNAUTHENTICATED);
        AccessDeniedHandler denied = (req, res, ex) -> write(res, mapper, ErrorCode.FORBIDDEN);
        Converter<Jwt, UsernamePasswordAuthenticationToken> converter = SecurityConfig::toAuthentication;

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/identity/auth/login", "/api/v1/identity/auth/refresh",
                                "/api/v1/identity/auth/pin-login", "/api/v1/identity/devices/activate",
                                "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.decoder(tokens.accessDecoder()).jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(denied))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied))
                .addFilterAfter(mustChangePasswordFilter(mapper), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /** Tài khoản bị buộc đổi mật khẩu chỉ được gọi các API đổi mật khẩu/đăng xuất/xem hồ sơ (FR-CC-004). */
    private static OncePerRequestFilter mustChangePasswordFilter(ObjectMapper mapper) {
        Set<String> allowed = Set.of("/api/v1/identity/auth/change-password", "/api/v1/identity/auth/logout",
                "/api/v1/identity/me");
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getAuthorities().stream()
                        .anyMatch(a -> MUST_CHANGE_PASSWORD.equals(a.getAuthority()))
                        && !allowed.contains(req.getRequestURI())) {
                    write(res, mapper, ErrorCode.FORBIDDEN, "Bạn cần đổi mật khẩu trước khi tiếp tục sử dụng");
                    return;
                }
                chain.doFilter(req, res);
            }
        };
    }

    private static UsernamePasswordAuthenticationToken toAuthentication(Jwt jwt) {
        PxvbPrincipal principal = TokenService.toPrincipal(jwt);
        // Chưa đổi mật khẩu lần đầu: không cấp quyền nghiệp vụ nào, chỉ cấp authority đánh dấu.
        var authorities = Boolean.TRUE.equals(jwt.getClaim(TokenService.CLAIM_MUST_CHANGE_PASSWORD))
                ? List.of(new SimpleGrantedAuthority(MUST_CHANGE_PASSWORD))
                : principal.permissions().stream().map(SimpleGrantedAuthority::new).toList();
        return UsernamePasswordAuthenticationToken.authenticated(principal, jwt, authorities);
    }

    private static void write(HttpServletResponse res, ObjectMapper mapper, ErrorCode code) throws IOException {
        write(res, mapper, code, code.title());
    }

    private static void write(HttpServletResponse res, ObjectMapper mapper, ErrorCode code, String detail)
            throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(code.status()), detail);
        pd.setTitle(code.title());
        pd.setProperty("code", code.code());
        pd.setProperty("errors", Map.of());
        pd.setProperty("traceId", UUID.randomUUID().toString());
        res.setStatus(code.status());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(res.getOutputStream(), pd);
    }
}
