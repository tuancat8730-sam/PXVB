package vn.pxvb.identity.contract;

import java.util.Optional;
import vn.pxvb.common.security.PxvbPrincipal;

/** Xác thực access token ngoài luồng HTTP (vd. STOMP CONNECT). Trả rỗng nếu token sai/hết hạn/chưa đổi mật khẩu. */
public interface AccessTokenVerifier {

    Optional<PxvbPrincipal> verify(String bearerToken);
}
