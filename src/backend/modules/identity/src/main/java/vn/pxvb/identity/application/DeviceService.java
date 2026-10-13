package vn.pxvb.identity.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.identity.application.TokenService.IssuedToken;
import vn.pxvb.identity.domain.Device;
import vn.pxvb.identity.domain.DevicePosition;
import vn.pxvb.identity.infrastructure.DeviceRepository;

/** Đăng ký & kích hoạt thiết bị bằng mã dùng một lần (FR-CC-002). */
@Service
public class DeviceService {

    public record DeviceWithActivation(Device device, String activationCode) {
    }

    public record ActivatedDevice(UUID deviceId, String name, String deviceToken, Instant expiresAt) {
    }

    // Bỏ các ký tự dễ nhầm (0/O, 1/I/L) để nhập tay trên tablet.
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DeviceRepository devices;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final IdentityProperties props;
    private final Clock clock;

    public DeviceService(DeviceRepository devices, PasswordEncoder encoder, TokenService tokens,
                         IdentityProperties props, Clock clock) {
        this.devices = devices;
        this.encoder = encoder;
        this.tokens = tokens;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Device> list() {
        return devices.findAll();
    }

    @Transactional
    public DeviceWithActivation register(String deviceCode, String name, DevicePosition position,
                                         String machineIdsJson) {
        if (devices.findByDeviceCode(deviceCode).isPresent()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Mã thiết bị đã tồn tại",
                    Map.of("deviceCode", List.of("Mã thiết bị đã tồn tại")));
        }
        Device device = devices.save(new Device(deviceCode, name, position, machineIdsJson));
        return new DeviceWithActivation(device, newActivation(device));
    }

    @Transactional
    public DeviceWithActivation regenerateActivation(UUID id) {
        Device device = devices.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return new DeviceWithActivation(device, newActivation(device));
    }

    @Transactional
    public ActivatedDevice activate(String deviceCode, String activationCode) {
        Instant now = clock.instant();
        Device device = devices.findByDeviceCode(deviceCode).orElse(null);
        if (device == null || !device.isActive() || !device.hasValidActivation(now)
                || !encoder.matches(activationCode, device.getActivationCodeHash())) {
            throw new BusinessException(ErrorCode.AUTH_FAILED, "Mã kích hoạt không đúng hoặc đã hết hạn");
        }
        device.markActivated(now);
        IssuedToken token = tokens.issueDeviceToken(device.getId());
        return new ActivatedDevice(device.getId(), device.getName(), token.value(), token.expiresAt());
    }

    @Transactional
    public void deactivate(UUID id) {
        devices.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)).deactivate();
    }

    private String newActivation(Device device) {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        device.issueActivation(encoder.encode(code.toString()), clock.instant().plus(props.activationTtl()));
        return code.toString();
    }
}
