package vn.pxvb.identity.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.identity.application.DeviceService;
import vn.pxvb.identity.application.DeviceService.ActivatedDevice;
import vn.pxvb.identity.application.DeviceService.DeviceWithActivation;
import vn.pxvb.identity.domain.Device;
import vn.pxvb.identity.domain.DevicePosition;

@RestController
@RequestMapping("/api/v1/identity/devices")
public class DeviceController {

    public record DeviceDto(UUID id, String deviceCode, String name, DevicePosition position, String machineIds,
                            boolean active, boolean activated, Instant lastSeenAt, Instant lastSyncAt,
                            int pendingCount) {
        static DeviceDto from(Device d) {
            return new DeviceDto(d.getId(), d.getDeviceCode(), d.getName(), d.getPosition(), d.getMachineIds(),
                    d.isActive(), d.getActivatedAt() != null, d.getLastSeenAt(), d.getLastSyncAt(),
                    d.getPendingCount());
        }
    }

    /** {@code activationCode} chỉ trả về một lần duy nhất khi tạo/cấp lại. */
    public record DeviceCreatedDto(DeviceDto device, String activationCode) {
        static DeviceCreatedDto from(DeviceWithActivation d) {
            return new DeviceCreatedDto(DeviceDto.from(d.device()), d.activationCode());
        }
    }

    public record RegisterDeviceRequest(@NotBlank(message = "Vui lòng nhập mã thiết bị") String deviceCode,
                                        @NotBlank(message = "Vui lòng nhập tên thiết bị") String name,
                                        @NotNull(message = "Vui lòng chọn vị trí") DevicePosition position,
                                        String machineIds) {
    }

    public record ActivateRequest(@NotBlank String deviceCode, @NotBlank String activationCode) {
    }

    public record ActivatedDto(UUID deviceId, String name, String deviceToken, Instant expiresAt) {
        static ActivatedDto from(ActivatedDevice a) {
            return new ActivatedDto(a.deviceId(), a.name(), a.deviceToken(), a.expiresAt());
        }
    }

    private final DeviceService service;

    public DeviceController(DeviceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('identity.device.view')")
    public List<DeviceDto> list() {
        return service.list().stream().map(DeviceDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('identity.device.create')")
    public DeviceCreatedDto register(@Valid @RequestBody RegisterDeviceRequest req) {
        return DeviceCreatedDto.from(service.register(req.deviceCode(), req.name(), req.position(),
                req.machineIds()));
    }

    @PostMapping("/{id}/activation-code")
    @PreAuthorize("hasAuthority('identity.device.create')")
    public DeviceCreatedDto regenerate(@PathVariable UUID id) {
        return DeviceCreatedDto.from(service.regenerateActivation(id));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('identity.device.update')")
    public void deactivate(@PathVariable UUID id) {
        service.deactivate(id);
    }

    /** Công khai: tablet dùng mã kích hoạt một lần để nhận device token. */
    @PostMapping("/activate")
    public ActivatedDto activate(@Valid @RequestBody ActivateRequest req) {
        return ActivatedDto.from(service.activate(req.deviceCode(), req.activationCode()));
    }
}
