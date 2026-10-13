package vn.pxvb.planning.application;

import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.audit.AuditLogWriter;
import vn.pxvb.common.audit.AuditLogWriter.FieldChange;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.outbox.OutboxPublisher;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.common.sequence.NumberSequenceService;
import vn.pxvb.identity.contract.IdentityDirectory;
import vn.pxvb.masterdata.contract.MasterdataQueries;
import vn.pxvb.masterdata.contract.MasterdataQueries.MachineInfo;
import vn.pxvb.masterdata.contract.MasterdataQueries.Norm;
import vn.pxvb.masterdata.contract.MasterdataQueries.PairingLine;
import vn.pxvb.masterdata.contract.MasterdataQueries.ProductInfo;
import vn.pxvb.planning.contract.PlanningEvents.OrderConfirmed;
import vn.pxvb.workflow.application.SignatureService;
import vn.pxvb.workflow.domain.Signature;

/**
 * Lệnh sản xuất ngày/ca (FR-PL-003/004/006): gợi ý tự động theo ghép cặp + sản phẩm được phép + trạng thái máy;
 * xác nhận kèm xác nhận điện tử và khóa phiên bản định mức tại thời điểm xác nhận; chỉ điều chỉnh bằng
 * {@code OrderAdjustment} sau khi xác nhận (BR-PL-002).
 */
@Service
public class OrderService {

    public record OrderInput(LocalDate businessDate, String shiftCode, UUID makerMachineId, UUID packerMachineId,
                             UUID productId, long plannedPacks) {
    }

    public record SuggestResult(List<Map<String, Object>> orders, List<String> skipped) {
    }

    private static final List<String> SHIFTS = List.of("A", "B", "C");
    private static final Set<String> CONFIRM_ROLES_ORDER_SET = Set.of("SHIFT_LEADER", "DEPUTY_DIRECTOR");

    private final JdbcTemplate jdbc;
    private final MasterdataQueries masterdata;
    private final NumberSequenceService numbers;
    private final SignatureService signatures;
    private final IdentityDirectory directory;
    private final OutboxPublisher outbox;
    private final ApplicationEventPublisher events;
    private final AuditLogWriter audit;
    private final CurrentUser currentUser;
    private final Clock clock;

    public OrderService(JdbcTemplate jdbc, MasterdataQueries masterdata, NumberSequenceService numbers,
                        SignatureService signatures, IdentityDirectory directory, OutboxPublisher outbox,
                        ApplicationEventPublisher events, AuditLogWriter audit, CurrentUser currentUser,
                        Clock clock) {
        this.jdbc = jdbc;
        this.masterdata = masterdata;
        this.numbers = numbers;
        this.signatures = signatures;
        this.directory = directory;
        this.outbox = outbox;
        this.events = events;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // ---------- Gợi ý ----------

    @Transactional
    public SuggestResult suggest(UUID weeklyPlanId, LocalDate from, LocalDate to) {
        List<Map<String, Object>> plan = jdbc.queryForList("SELECT status, week_start AS weekStart FROM "
                + "pl_weekly_plan WHERE id = UUID_TO_BIN(?)", weeklyPlanId.toString());
        if (plan.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy KHSX tuần");
        }
        if (!"APPROVED".equals(plan.get(0).get("status"))) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ sinh gợi ý lệnh từ KHSX tuần đã được duyệt");
        }
        LocalDate weekStart = ((java.sql.Date) plan.get(0).get("weekStart")).toLocalDate();
        LocalDate start = from == null ? weekStart : from;
        LocalDate end = to == null ? weekStart.plusDays(6) : to;
        if (start.isBefore(weekStart) || end.isAfter(weekStart.plusDays(6)) || end.isBefore(start)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Khoảng ngày phải nằm trong tuần của kế hoạch");
        }
        // Gợi ý mới thay thế gợi ý cũ chưa xác nhận; không bao giờ đụng lệnh đã xác nhận (BR-PL-004)
        jdbc.update("DELETE FROM pl_production_order WHERE status = 'SUGGESTED' AND source = 'AUTO' "
                + "AND business_date BETWEEN ? AND ?", start, end);

        List<String> skipped = new ArrayList<>();
        List<UUID> created = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            List<Map<String, Object>> dayItems = jdbc.queryForList("SELECT BIN_TO_UUID(product_id) AS productId, "
                    + "planned_packs AS packs FROM pl_weekly_plan_item WHERE weekly_plan_id = UUID_TO_BIN(?) "
                    + "AND plan_date = ?", weeklyPlanId.toString(), date);
            if (dayItems.isEmpty()) {
                continue;
            }
            Map<String, List<PairingLine>> pairings = new LinkedHashMap<>();
            Set<UUID> machineIds = new HashSet<>();
            for (String shift : SHIFTS) {
                List<PairingLine> lines = masterdata.resolvePairing(date, shift);
                pairings.put(shift, lines);
                lines.forEach(l -> {
                    machineIds.add(l.makerMachineId());
                    machineIds.add(l.packerMachineId());
                });
            }
            Map<UUID, MachineInfo> machines = masterdata.machines(machineIds);
            Set<String> usedSlots = new HashSet<>();
            jdbc.queryForList("SELECT shift_code, BIN_TO_UUID(packer_machine_id) AS packer FROM pl_production_order "
                    + "WHERE business_date = ? AND status <> 'CANCELLED'", date)
                    .forEach(r -> usedSlots.add(r.get("shift_code") + "|" + r.get("packer")));

            List<UUID> productIds = dayItems.stream().map(r -> UUID.fromString((String) r.get("productId"))).toList();
            Map<UUID, ProductInfo> products = masterdata.products(productIds);
            for (Map<String, Object> item : dayItems) {
                UUID productId = UUID.fromString((String) item.get("productId"));
                // Trừ phần đã nằm trong lệnh xác nhận/đang chạy để không phân bổ dư khi sinh lại gợi ý
                Long existing = jdbc.queryForObject("SELECT COALESCE(SUM(planned_packs), 0) FROM "
                        + "pl_production_order WHERE business_date = ? AND product_id = UUID_TO_BIN(?) "
                        + "AND status NOT IN ('CANCELLED','SUGGESTED')", Long.class, date, productId.toString());
                long total = ((Number) item.get("packs")).longValue() - (existing == null ? 0 : existing);
                if (total <= 0) {
                    continue;
                }
                Set<UUID> allowed = masterdata.allowedMachineIds(productId);
                String productLabel = products.containsKey(productId) ? products.get(productId).code()
                        : productId.toString();
                List<String[]> candidates = new ArrayList<>();
                for (String shift : SHIFTS) {
                    for (PairingLine l : pairings.get(shift)) {
                        MachineInfo packer = machines.get(l.packerMachineId());
                        MachineInfo maker = machines.get(l.makerMachineId());
                        String slot = shift + "|" + l.packerMachineId();
                        if (packer == null || maker == null || usedSlots.contains(slot)) {
                            continue;
                        }
                        if (!"ACTIVE".equals(packer.status()) || !packer.active()) {
                            skipped.add(date + " ca " + shift + ": bỏ qua máy bao " + packer.code() + " ("
                                    + packer.status() + ")");
                            continue;
                        }
                        if (!"ACTIVE".equals(maker.status()) || !maker.active()) {
                            skipped.add(date + " ca " + shift + ": bỏ qua máy vấn " + maker.code() + " ("
                                    + maker.status() + ")");
                            continue;
                        }
                        if (!allowed.isEmpty() && !allowed.contains(l.packerMachineId())) {
                            continue;
                        }
                        candidates.add(new String[] {shift, l.makerMachineId().toString(),
                                l.packerMachineId().toString(), slot});
                    }
                }
                if (candidates.isEmpty()) {
                    skipped.add(date + " " + productLabel + ": không có máy khả dụng để phân bổ");
                    continue;
                }
                long base = total / candidates.size();
                long remainder = total % candidates.size();
                for (int i = 0; i < candidates.size(); i++) {
                    long packs = base + (i == 0 ? remainder : 0);
                    if (packs <= 0) {
                        continue;
                    }
                    String[] c = candidates.get(i);
                    usedSlots.add(c[3]);
                    created.add(insertOrder(new OrderInput(date, c[0], UUID.fromString(c[1]), UUID.fromString(c[2]),
                            productId, packs), "AUTO", weeklyPlanId));
                }
            }
        }
        audit(weeklyPlanId, "WeeklyPlan", "SUGGEST_ORDERS", List.of(new FieldChange("orders", null,
                created.size())));
        return new SuggestResult(created.stream().map(this::get).toList(), skipped);
    }

    @Transactional
    public Map<String, Object> createManual(OrderInput in) {
        validate(in);
        return get(insertOrder(in, "MANUAL", null));
    }

    // ---------- Truy vấn ----------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(LocalDate date, String shift, UUID machineId, String status) {
        StringBuilder sql = new StringBuilder("SELECT BIN_TO_UUID(id) FROM pl_production_order WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (date != null) {
            sql.append(" AND business_date = ?");
            params.add(date);
        }
        if (shift != null && !shift.isBlank()) {
            sql.append(" AND shift_code = ?");
            params.add(shift.toUpperCase());
        }
        if (machineId != null) {
            sql.append(" AND (maker_machine_id = UUID_TO_BIN(?) OR packer_machine_id = UUID_TO_BIN(?))");
            params.add(machineId.toString());
            params.add(machineId.toString());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY business_date, shift_code, code LIMIT 1000");
        return jdbc.queryForList(sql.toString(), String.class, params.toArray()).stream()
                .map(id -> get(UUID.fromString(id))).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, code, business_date AS "
                + "businessDate, shift_code AS shiftCode, BIN_TO_UUID(maker_machine_id) AS makerMachineId, "
                + "BIN_TO_UUID(packer_machine_id) AS packerMachineId, BIN_TO_UUID(product_id) AS productId, "
                + "planned_packs AS plannedPacks, status, BIN_TO_UUID(norm_version_id) AS normVersionId, "
                + "BIN_TO_UUID(weekly_plan_id) AS weeklyPlanId, BIN_TO_UUID(confirmed_by) AS confirmedBy, "
                + "confirmed_at AS confirmedAt, source, revision, cancel_reason AS cancelReason, "
                + "row_version AS rowVersion FROM pl_production_order WHERE id = UUID_TO_BIN(?)", id.toString());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy lệnh sản xuất");
        }
        Map<String, Object> view = new LinkedHashMap<>(rows.get(0));
        Map<UUID, MachineInfo> machines = masterdata.machines(List.of(
                UUID.fromString((String) view.get("makerMachineId")),
                UUID.fromString((String) view.get("packerMachineId"))));
        MachineInfo maker = machines.get(UUID.fromString((String) view.get("makerMachineId")));
        MachineInfo packer = machines.get(UUID.fromString((String) view.get("packerMachineId")));
        view.put("makerCode", maker == null ? null : maker.code());
        view.put("packerCode", packer == null ? null : packer.code());
        ProductInfo product = masterdata.products(List.of(UUID.fromString((String) view.get("productId"))))
                .get(UUID.fromString((String) view.get("productId")));
        view.put("productCode", product == null ? null : product.code());
        long packs = ((Number) view.get("plannedPacks")).longValue();
        view.put("plannedRods", product == null || product.rodsPerPack() == null ? null
                : packs * product.rodsPerPack());
        view.put("segments", jdbc.queryForList("SELECT segment_no AS segmentNo, BIN_TO_UUID(product_id) AS "
                + "productId, planned_packs AS plannedPacks, start_at AS startAt, end_at AS endAt, reason "
                + "FROM pl_order_segment WHERE order_id = UUID_TO_BIN(?) ORDER BY segment_no", id.toString()));
        return view;
    }

    // ---------- Sửa / xác nhận ----------

    @Transactional
    public Map<String, Object> update(UUID id, UUID makerId, UUID packerId, UUID productId, Long plannedPacks) {
        Map<String, Object> order = get(id);
        String status = (String) order.get("status");
        if (!"SUGGESTED".equals(status) && !"CONFIRMED".equals(status)) {
            throw new BusinessException(ErrorCode.DATA_LOCKED,
                    "Lệnh đang chạy/đã đóng không sửa trực tiếp; hãy dùng điều chỉnh trong ca (BR-PL-002)");
        }
        OrderInput merged = new OrderInput(((java.sql.Date) order.get("businessDate")).toLocalDate(),
                (String) order.get("shiftCode"),
                makerId != null ? makerId : UUID.fromString((String) order.get("makerMachineId")),
                packerId != null ? packerId : UUID.fromString((String) order.get("packerMachineId")),
                productId != null ? productId : UUID.fromString((String) order.get("productId")),
                plannedPacks != null ? plannedPacks : ((Number) order.get("plannedPacks")).longValue());
        validate(merged);
        boolean wasConfirmed = "CONFIRMED".equals(status);
        jdbc.update("UPDATE pl_production_order SET maker_machine_id = UUID_TO_BIN(?), packer_machine_id = "
                        + "UUID_TO_BIN(?), product_id = UUID_TO_BIN(?), planned_packs = ?, status = 'SUGGESTED', "
                        + "confirmed_by = NULL, confirmed_at = NULL, norm_version_id = NULL, revision = revision "
                        + (wasConfirmed ? "+ 1" : "") + ", row_version = row_version + 1, "
                        + "updated_at = UTC_TIMESTAMP(6), updated_by = UUID_TO_BIN(?) WHERE id = UUID_TO_BIN(?)",
                merged.makerMachineId().toString(), merged.packerMachineId().toString(),
                merged.productId().toString(), merged.plannedPacks(), user(), id.toString());
        jdbc.update("UPDATE pl_order_segment SET product_id = UUID_TO_BIN(?), planned_packs = ? "
                        + "WHERE order_id = UUID_TO_BIN(?) AND segment_no = 1", merged.productId().toString(),
                merged.plannedPacks(), id.toString());
        if (wasConfirmed) {
            outbox.publish("pl.order.revised", Map.of("orderId", id.toString(), "code", order.get("code")));
        }
        audit(id, "ProductionOrder", wasConfirmed ? "REVISE" : "UPDATE", List.of(
                new FieldChange("plannedPacks", order.get("plannedPacks"), merged.plannedPacks())));
        return get(id);
    }

    @Transactional
    public Map<String, Object> confirm(UUID id, UUID userId, UUID deviceId, Signature.Method method, String secret) {
        Map<String, Object> order = get(id);
        if (!"SUGGESTED".equals(order.get("status"))) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ xác nhận được lệnh ở trạng thái SUGGESTED (hiện tại " + order.get("status") + ")");
        }
        UUID productId = UUID.fromString((String) order.get("productId"));
        LocalDate date = ((java.sql.Date) order.get("businessDate")).toLocalDate();
        Norm norm = masterdata.activeNorm(productId, date).orElseThrow(() -> new BusinessException(
                ErrorCode.VALIDATION, "Sản phẩm " + order.get("productCode") + " chưa có định mức hiệu lực"));
        String role = directory.roleCodesOf(userId).stream().filter(CONFIRM_ROLES_ORDER_SET::contains)
                .min(String::compareTo).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN,
                        "Chỉ Trưởng ca/Phó Quản đốc được xác nhận lệnh sản xuất"));
        signatures.sign("ProductionOrder", id, Signature.Purpose.CONFIRM, role, userId, deviceId, method, secret,
                SignatureService.hashOf(canonical(order)));
        jdbc.update("UPDATE pl_production_order SET status = 'CONFIRMED', confirmed_by = UUID_TO_BIN(?), "
                        + "confirmed_at = UTC_TIMESTAMP(6), norm_version_id = UUID_TO_BIN(?), "
                        + "row_version = row_version + 1, updated_at = UTC_TIMESTAMP(6), updated_by = UUID_TO_BIN(?) "
                        + "WHERE id = UUID_TO_BIN(?)", userId.toString(), norm.normId().toString(),
                userId.toString(), id.toString());
        int revision = ((Number) order.get("revision")).intValue();
        long packs = ((Number) order.get("plannedPacks")).longValue();
        events.publishEvent(new OrderConfirmed(id, (String) order.get("code"), date, (String) order.get("shiftCode"),
                UUID.fromString((String) order.get("makerMachineId")),
                UUID.fromString((String) order.get("packerMachineId")), productId, packs, norm.normId(), revision));
        outbox.publish("pl.order.confirmed", Map.of("orderId", id.toString(), "code", order.get("code"),
                "businessDate", date.toString(), "shiftCode", order.get("shiftCode"),
                "productId", productId.toString(), "normVersionId", norm.normId().toString(),
                "plannedPacks", packs));
        audit(id, "ProductionOrder", "CONFIRM", List.of(new FieldChange("status", "SUGGESTED", "CONFIRMED"),
                new FieldChange("normVersionId", null, norm.normId().toString())));
        return get(id);
    }

    @Transactional
    public List<Map<String, Object>> batchConfirm(List<UUID> ids, UUID userId, UUID deviceId,
                                                  Signature.Method method, String secret) {
        List<Map<String, Object>> confirmed = new ArrayList<>();
        for (UUID id : ids) {
            confirmed.add(confirm(id, userId, deviceId, method, secret));
        }
        return confirmed;
    }

    @Transactional
    public Map<String, Object> cancel(UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần nhập lý do hủy lệnh",
                    Map.of("reason", List.of("Vui lòng nhập lý do")));
        }
        Map<String, Object> order = get(id);
        if (!Set.of("SUGGESTED", "CONFIRMED").contains((String) order.get("status"))) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Không hủy được lệnh đang chạy/đã đóng");
        }
        jdbc.update("UPDATE pl_production_order SET status = 'CANCELLED', cancel_reason = ?, "
                + "row_version = row_version + 1, updated_at = UTC_TIMESTAMP(6) WHERE id = UUID_TO_BIN(?)",
                reason.trim(), id.toString());
        audit(id, "ProductionOrder", "CANCEL", List.of(new FieldChange("status", order.get("status"),
                "CANCELLED")));
        return get(id);
    }

    /** Điều chỉnh trong ca (đổi mác/hết sợi/máy hỏng/chạy phụ/đổi sản lượng) — FR-PL-006. */
    @Transactional
    public Map<String, Object> adjust(UUID id, String type, String reasonCode, String note, UUID newProductId,
                                      Long newPlannedPacks, UUID userId) {
        Map<String, Object> order = get(id);
        if (!Set.of("CONFIRMED", "RUNNING").contains((String) order.get("status"))) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ điều chỉnh được lệnh đã xác nhận hoặc đang chạy");
        }
        if (!Set.of("PRODUCT_CHANGE", "FIBER_OUT", "BREAKDOWN", "SIDE_RUN", "QTY_CHANGE", "OTHER").contains(type)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Loại điều chỉnh không hợp lệ: " + type);
        }
        if (reasonCode == null || !masterdata.reasonExists("ORDER_ADJUST", reasonCode)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần chọn lý do điều chỉnh hợp lệ (ORDER_ADJUST)",
                    Map.of("reasonCode", List.of("Vui lòng chọn lý do trong danh mục")));
        }
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("productId", order.get("productId"));
        before.put("plannedPacks", order.get("plannedPacks"));
        Map<String, Object> after = new LinkedHashMap<>(before);
        String suggestion = null;
        Instant now = clock.instant();
        if ("PRODUCT_CHANGE".equals(type)) {
            if (newProductId == null) {
                throw new BusinessException(ErrorCode.VALIDATION, "Đổi mác cần chọn sản phẩm mới");
            }
            ProductInfo p = masterdata.products(List.of(newProductId)).get(newProductId);
            if (p == null || !p.active()) {
                throw new BusinessException(ErrorCode.VALIDATION, "Sản phẩm mới không tồn tại hoặc đã ngừng");
            }
            Set<UUID> allowed = masterdata.allowedMachineIds(newProductId);
            UUID packer = UUID.fromString((String) order.get("packerMachineId"));
            if (!allowed.isEmpty() && !allowed.contains(packer)) {
                throw new BusinessException(ErrorCode.VALIDATION, "Máy bao không được phép chạy sản phẩm mới");
            }
            jdbc.update("UPDATE pl_order_segment SET end_at = ? WHERE order_id = UUID_TO_BIN(?) AND end_at IS NULL",
                    java.sql.Timestamp.from(now), id.toString());
            Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(segment_no), 0) + 1 FROM pl_order_segment "
                    + "WHERE order_id = UUID_TO_BIN(?)", Integer.class, id.toString());
            long packs = newPlannedPacks != null ? newPlannedPacks : ((Number) order.get("plannedPacks")).longValue();
            jdbc.update("INSERT INTO pl_order_segment(id, order_id, segment_no, product_id, planned_packs, start_at, "
                            + "reason) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, UUID_TO_BIN(?), ?, ?, ?)",
                    UuidCreator.getTimeOrderedEpoch().toString(), id.toString(), next, newProductId.toString(),
                    packs, java.sql.Timestamp.from(now), reasonCode);
            jdbc.update("UPDATE pl_production_order SET product_id = UUID_TO_BIN(?), row_version = row_version + 1, "
                    + "updated_at = UTC_TIMESTAMP(6) WHERE id = UUID_TO_BIN(?)", newProductId.toString(),
                    id.toString());
            after.put("productId", newProductId.toString());
            suggestion = "Thời gian chuyển đổi mác nên ghi nhận bằng mã dừng A (do PR ghi); cần cấp phát NVL cho mác mới.";
        }
        if (newPlannedPacks != null) {
            if (newPlannedPacks <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION, "Sản lượng phải lớn hơn 0");
            }
            long old = ((Number) order.get("plannedPacks")).longValue();
            jdbc.update("UPDATE pl_production_order SET planned_packs = ?, row_version = row_version + 1, "
                    + "updated_at = UTC_TIMESTAMP(6) WHERE id = UUID_TO_BIN(?)", newPlannedPacks, id.toString());
            after.put("plannedPacks", newPlannedPacks);
            long delta = newPlannedPacks - old;
            if (delta != 0) {
                suggestion = (delta < 0 ? "Thiếu " : "Thừa ") + Math.abs(delta)
                        + " bao so với kế hoạch: đề xuất bù/giảm vào các ca còn lại trong ngày hoặc ngày tiếp theo.";
            }
        }
        jdbc.update("INSERT INTO pl_order_adjustment(id, order_id, adj_type, reason_code, note, before_json, "
                        + "after_json, adjusted_at, adjusted_by) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, "
                        + "CAST(? AS JSON), CAST(? AS JSON), UTC_TIMESTAMP(6), UUID_TO_BIN(?))",
                UuidCreator.getTimeOrderedEpoch().toString(), id.toString(), type, reasonCode, note,
                toJson(before), toJson(after), userId.toString());
        outbox.publish("pl.order.adjusted", Map.of("orderId", id.toString(), "code", order.get("code"),
                "type", type));
        audit(id, "ProductionOrder", "ADJUST", List.of(new FieldChange("adjustment", before, after)));
        Map<String, Object> view = get(id);
        view.put("suggestion", suggestion);
        return view;
    }

    // ---------- Nội bộ ----------

    private UUID insertOrder(OrderInput in, String source, UUID weeklyPlanId) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        String ymd = in.businessDate().toString().replace("-", "");
        String code = numbers.next("lsx-" + ymd + "-" + in.shiftCode(), "LSX-" + ymd + "-" + in.shiftCode(),
                NumberSequenceService.ResetPolicy.NONE);
        String user = user();
        try {
            jdbc.update("INSERT INTO pl_production_order(id, row_version, created_at, created_by, updated_at, "
                            + "updated_by, code, business_date, shift_code, maker_machine_id, packer_machine_id, "
                            + "product_id, planned_packs, weekly_plan_id, status, source, revision) VALUES ("
                            + "UUID_TO_BIN(?), 0, UTC_TIMESTAMP(6), UUID_TO_BIN(?), UTC_TIMESTAMP(6), UUID_TO_BIN(?), "
                            + "?, ?, ?, UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, UUID_TO_BIN(?), "
                            + "'SUGGESTED', ?, 1)", id.toString(), user, user, code, in.businessDate(), in.shiftCode(),
                    in.makerMachineId().toString(), in.packerMachineId().toString(), in.productId().toString(),
                    in.plannedPacks(), weeklyPlanId == null ? null : weeklyPlanId.toString(), source);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "Máy bao đã có lệnh trong ca " + in.shiftCode()
                    + " ngày " + in.businessDate());
        }
        jdbc.update("INSERT INTO pl_order_segment(id, order_id, segment_no, product_id, planned_packs) VALUES ("
                        + "UUID_TO_BIN(?), UUID_TO_BIN(?), 1, UUID_TO_BIN(?), ?)",
                UuidCreator.getTimeOrderedEpoch().toString(), id.toString(), in.productId().toString(),
                in.plannedPacks());
        audit(id, "ProductionOrder", "CREATE", List.of(new FieldChange("code", null, code)));
        return id;
    }

    private void validate(OrderInput in) {
        if (in.businessDate() == null || !SHIFTS.contains(in.shiftCode() == null ? "" : in.shiftCode())) {
            throw new BusinessException(ErrorCode.VALIDATION, "Ngày/ca không hợp lệ (ca A, B, C)");
        }
        if (in.plannedPacks() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION, "Sản lượng kế hoạch phải lớn hơn 0");
        }
        Map<UUID, MachineInfo> machines = masterdata.machines(List.of(in.makerMachineId(), in.packerMachineId()));
        MachineInfo maker = machines.get(in.makerMachineId());
        MachineInfo packer = machines.get(in.packerMachineId());
        if (maker == null || !"MAKER".equals(maker.machineType())) {
            throw new BusinessException(ErrorCode.VALIDATION, "Máy vấn không hợp lệ");
        }
        if (packer == null || !"PACKER".equals(packer.machineType())) {
            throw new BusinessException(ErrorCode.VALIDATION, "Máy bao không hợp lệ");
        }
        ProductInfo product = masterdata.products(List.of(in.productId())).get(in.productId());
        if (product == null || !product.active()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Sản phẩm không tồn tại hoặc đã ngừng");
        }
        Set<UUID> allowed = masterdata.allowedMachineIds(in.productId());
        if (!allowed.isEmpty() && !allowed.contains(in.packerMachineId())) {
            throw new BusinessException(ErrorCode.VALIDATION,
                    "Sản phẩm " + product.code() + " không được phép chạy trên máy " + packer.code());
        }
    }

    private static String canonical(Map<String, Object> o) {
        return String.join("|", String.valueOf(o.get("code")), String.valueOf(o.get("businessDate")),
                String.valueOf(o.get("shiftCode")), String.valueOf(o.get("makerMachineId")),
                String.valueOf(o.get("packerMachineId")), String.valueOf(o.get("productId")),
                String.valueOf(o.get("plannedPacks")), String.valueOf(o.get("revision")));
    }

    private String toJson(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String user() {
        return currentUser.userId().map(UUID::toString).orElse(null);
    }

    private void audit(UUID id, String entityType, String action, List<FieldChange> changes) {
        var principal = currentUser.principal();
        audit.write(new AuditLogWriter.Entry("pl", entityType, id.toString(), action, changes, null,
                principal.map(p -> p.userId()).orElse(null), null, principal.map(p -> p.deviceId()).orElse(null),
                null));
    }
}
