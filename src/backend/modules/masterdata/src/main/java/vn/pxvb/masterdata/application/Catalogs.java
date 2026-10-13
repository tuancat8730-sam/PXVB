package vn.pxvb.masterdata.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vn.pxvb.masterdata.application.CatalogSpec.Col;
import vn.pxvb.masterdata.application.CatalogSpec.Type;

/**
 * Danh mục masterdata khai báo bằng đặc tả (SDD §5.2). Phần có luật riêng (định mức có phiên bản, ghép cặp
 * vấn–bao, tra cứu ngưỡng) nằm ở các dịch vụ riêng; ở đây là các danh mục CRUD/import/export thuần.
 */
public final class Catalogs {

    /** Mọi vai trò nghiệp vụ được xem masterdata (không có dữ liệu nhạy cảm). */
    public static final Set<String> VIEWERS = Set.of("SYS_ADMIN", "DIRECTOR", "DEPUTY_DIRECTOR", "PLANNER",
            "NORM_CLERK", "SHIFT_LEADER", "SUPERVISOR", "OPERATOR", "STAT_OUTPUT", "STAT_MATERIAL",
            "MATERIAL_ISSUER", "STAT_STAMP", "QC_INLINE", "QA_INSPECTOR", "QA_SUPERVISOR", "QA_HEAD", "MNT_TECH",
            "MNT_LEAD", "MECHANIC", "WAREHOUSE_KEEPER", "KTCD", "PXCD", "KHVT", "TCKT", "BOARD", "QA_DEPT");

    private static final Map<String, CatalogSpec> BY_RESOURCE = new LinkedHashMap<>();

    public static final CatalogSpec MACHINES = register(
            CatalogSpec.builder("machines", "md_machine", "Machine", "machine", "Máy móc thiết bị")
                    .col(s("code", "Mã máy", 50).req().imm().srch())
                    .col(s("name", "Tên máy", 200).req().srch())
                    .col(e("machineType", "Loại máy", "MAKER", "PACKER", "FEEDER", "OTHER").req())
                    .col(s("model", "Dòng máy", 50))
                    .col(e("rodFormat", "Khổ điếu", "COMPACT", "DEMI", "SLIM", "SUPER_SLIM"))
                    .col(d("theoreticalRate", "Công suất lý thuyết"))
                    .col(e("rateUnit", "Đơn vị công suất", "K_RODS_PER_MIN", "PACKS_PER_MIN"))
                    .col(s("lineCode", "Dây chuyền", 50))
                    .col(s("location", "Vị trí", 100))
                    .col(b("hasIpc", "Có IPC").def(false))
                    .col(i("tabletCount", "Số tablet").def(0))
                    .col(e("status", "Trạng thái", "ACTIVE", "INACTIVE", "UNDER_MAINTENANCE", "DECOMMISSIONED")
                            .req().def("ACTIVE"))
                    .col(dt("installDate", "Ngày lắp đặt"))
                    .col(s("erpAssetCode", "Mã tài sản ERP", 50))
                    .keys("code").status("status", "ACTIVE", "INACTIVE")
                    .writers("SYS_ADMIN").approvers("DEPUTY_DIRECTOR")
                    .usage("Ghép cặp vấn–bao", "SELECT COUNT(*) FROM md_machine_pairing_line "
                            + "WHERE maker_machine_id = UUID_TO_BIN(?) OR packer_machine_id = UUID_TO_BIN(?)")
                    .usage("Cụm/bộ phận", "SELECT COUNT(*) FROM md_machine_component WHERE machine_id = UUID_TO_BIN(?)")
                    .usage("Sản phẩm được phép chạy", "SELECT COUNT(*) FROM md_product_machine_allowed "
                            + "WHERE machine_id = UUID_TO_BIN(?)")
                    .usage("Liên kết phụ tùng", "SELECT COUNT(*) FROM md_machine_part_link WHERE machine_id = UUID_TO_BIN(?)")
                    .build());

    public static final CatalogSpec COMPONENTS = register(
            CatalogSpec.builder("machine-components", "md_machine_component", "MachineComponent", "component",
                            "Cụm/bộ phận máy")
                    .col(ref("machineId", "Máy", "md_machine", "code").req().imm())
                    .col(ref("parentId", "Cụm cha", "md_machine_component", "code"))
                    .col(s("code", "Mã cụm", 50).req().imm().srch())
                    .col(s("name", "Tên cụm", 200).req().srch())
                    .col(e("discipline", "Chuyên ngành", "MECHANICAL", "ELECTRICAL", "OTHER").req())
                    .col(s("kind", "Loại cụm", 30))
                    .col(i("sortOrder", "Thứ tự").def(0))
                    .keys("machineId", "code")
                    .writers("SYS_ADMIN").approvers("DEPUTY_DIRECTOR")
                    .usage("Cụm con", "SELECT COUNT(*) FROM md_machine_component WHERE parent_id = UUID_TO_BIN(?)")
                    .usage("Liên kết phụ tùng", "SELECT COUNT(*) FROM md_machine_part_link "
                            + "WHERE component_id = UUID_TO_BIN(?)")
                    .build());

    public static final CatalogSpec STAMP_TYPES = register(
            CatalogSpec.builder("stamp-types", "md_stamp_type", "StampType", "stampType", "Loại tem thuế")
                    .col(s("code", "Mã loại tem", 50).req().imm().srch())
                    .col(s("name", "Tên loại tem", 200).req().srch())
                    .col(s("notes", "Ghi chú", 500))
                    .keys("code").writers("SYS_ADMIN")
                    .usage("Sản phẩm", "SELECT COUNT(*) FROM md_product WHERE stamp_type_id = UUID_TO_BIN(?)")
                    .build());

    public static final CatalogSpec PRODUCTS = register(
            CatalogSpec.builder("products", "md_product", "Product", "product", "Sản phẩm")
                    .col(s("code", "Mã sản phẩm", 50).req().imm().srch())
                    .col(s("name", "Tên sản phẩm", 200).req().srch())
                    .col(e("productType", "Loại sản phẩm", "DOMESTIC", "EXPORT", "PROCESSING").req())
                    .col(e("rodFormat", "Khổ điếu", "COMPACT", "DEMI", "SLIM", "SUPER_SLIM"))
                    .col(i("rodsPerPack", "Số điếu/bao"))
                    .col(i("packsPerCarton", "Số bao/tút"))
                    .col(i("cartonsPerCase", "Số tút/thùng"))
                    .col(b("requiresExpiryInkjet", "In hạn dùng").def(false))
                    .col(ref("stampTypeId", "Loại tem", "md_stamp_type", "code"))
                    .col(i("stampsPerPack", "Số tem/bao").def(1))
                    .col(e("status", "Trạng thái", "ACTIVE", "DISCONTINUED").req().def("ACTIVE"))
                    .keys("code").status("status", "ACTIVE", "DISCONTINUED")
                    .writers("SYS_ADMIN", "PLANNER").approvers("DEPUTY_DIRECTOR")
                    .usage("Định mức nguyên vật liệu", "SELECT COUNT(*) FROM md_bom_norm "
                            + "WHERE product_id = UUID_TO_BIN(?) AND status IN ('PENDING','ACTIVE')")
                    .build());

    public static final CatalogSpec PRODUCT_ALLOWED = register(
            CatalogSpec.builder("product-allowed-machines", "md_product_machine_allowed", "ProductMachineAllowed",
                            "productMachine", "Máy được phép chạy sản phẩm")
                    .col(ref("productId", "Sản phẩm", "md_product", "code").req().imm())
                    .col(ref("machineId", "Máy", "md_machine", "code").req().imm())
                    .keys("productId", "machineId").noActive()
                    .writers("SYS_ADMIN", "PLANNER").approvers("DEPUTY_DIRECTOR")
                    .build());

    public static final CatalogSpec PHYSICAL_SPECS = register(
            CatalogSpec.builder("product-physical-specs", "md_product_physical_spec", "ProductPhysicalSpec",
                            "physicalSpec", "Thông số vật lý sản phẩm")
                    .col(ref("productId", "Sản phẩm", "md_product", "code").req().imm())
                    .col(e("indicator", "Chỉ tiêu", "WEIGHT", "PRESSURE_DROP", "CIRCUMFERENCE", "MOISTURE",
                            "HARDNESS", "DRAW_POINT").req().imm())
                    .col(e("category", "Phân loại", "DOMESTIC", "EXPORT", "PROCESSING").req().imm())
                    .col(d("target", "Giá trị đích"))
                    .col(d("minValue", "Giá trị nhỏ nhất"))
                    .col(d("maxValue", "Giá trị lớn nhất"))
                    .col(s("uom", "Đơn vị", 20))
                    .col(dt("effectiveFrom", "Hiệu lực từ").req().imm())
                    .col(dt("effectiveTo", "Hiệu lực đến"))
                    .keys("productId", "indicator", "category", "effectiveFrom").noActive()
                    .writers("SYS_ADMIN", "QA_HEAD").approvers("QA_HEAD")
                    .build());

    public static final CatalogSpec MATERIAL_CATEGORIES = register(
            CatalogSpec.builder("material-categories", "md_material_category", "MaterialCategory",
                            "materialCategory", "Nhóm nguyên vật liệu")
                    .col(s("code", "Mã nhóm", 30).req().imm().srch())
                    .col(s("name", "Tên nhóm", 200).req().srch())
                    .keys("code").writers("SYS_ADMIN")
                    .usage("Nguyên vật liệu", "SELECT COUNT(*) FROM md_material m JOIN md_material_category c "
                            + "ON c.code = m.category_code WHERE c.id = UUID_TO_BIN(?)")
                    .build());

    public static final CatalogSpec MATERIALS = register(
            CatalogSpec.builder("materials", "md_material", "Material", "material", "Nguyên vật liệu")
                    .col(s("code", "Mã NVL", 50).req().imm().srch())
                    .col(s("name", "Tên NVL", 200).req().srch())
                    .col(s("uom", "Đơn vị tính", 20).req())
                    .col(cref("categoryCode", "Nhóm (FIBER/ROLLING/PACKING)", "md_material_category").req())
                    .col(s("groupCode", "Nhóm ngưỡng hao hụt", 50))
                    .col(s("erpCode", "Mã ERP", 50))
                    .keys("code").writers("SYS_ADMIN", "NORM_CLERK").approvers("DEPUTY_DIRECTOR")
                    .usage("Dòng định mức", "SELECT COUNT(*) FROM md_bom_norm_line l JOIN md_bom_norm n ON n.id = l.norm_id "
                            + "WHERE l.material_id = UUID_TO_BIN(?) AND n.status IN ('PENDING','ACTIVE')")
                    .build());

    public static final CatalogSpec SHIFTS = register(
            CatalogSpec.builder("shift-defs", "md_shift_def", "ShiftDef", "shift", "Ca làm việc")
                    .col(s("code", "Mã ca", 10).req().imm().srch())
                    .col(s("name", "Tên ca", 100).req())
                    .col(tm("startTime", "Giờ bắt đầu").req())
                    .col(tm("endTime", "Giờ kết thúc").req())
                    .col(b("crossesMidnight", "Vắt qua nửa đêm").def(false))
                    .col(i("seq", "Thứ tự").req())
                    .keys("code").writers("SYS_ADMIN").approvers("DEPUTY_DIRECTOR")
                    .build());

    public static final CatalogSpec ISSUE_SLOTS = register(
            CatalogSpec.builder("issue-slots", "md_issue_slot_def", "IssueSlotDef", "issueSlot",
                            "Khung giờ cấp phát NVL")
                    .col(cref("shiftCode", "Mã ca", "md_shift_def").req().imm())
                    .col(i("slotNo", "Khung số (1–4)").req().imm())
                    .col(e("label", "Nhãn", "SANG", "TRUA", "CHIEU", "DEM").req())
                    .col(tm("startTime", "Giờ bắt đầu"))
                    .col(tm("endTime", "Giờ kết thúc"))
                    .col(d("sharePct", "Tỷ lệ chia phiếu (%)").req())
                    .keys("shiftCode", "slotNo").writers("SYS_ADMIN").approvers("DEPUTY_DIRECTOR")
                    .build());

    public static final CatalogSpec EMPLOYEES = register(
            CatalogSpec.builder("employees", "md_employee", "Employee", "employee", "Nhân sự")
                    .col(s("code", "Mã nhân viên", 50).req().imm().srch())
                    .col(s("fullName", "Họ tên", 200).req().srch())
                    .col(s("badgeNo", "Số thẻ", 50))
                    .col(s("orgUnitCode", "Đơn vị", 50))
                    .col(s("jobTitle", "Chức danh", 100))
                    .col(e("tradeSkill", "Kỹ năng", "MECHANICAL", "ELECTRICAL", "OTHER"))
                    .col(u("userId", "Tài khoản hệ thống"))
                    .keys("code").writers("SYS_ADMIN")
                    .build());

    public static final CatalogSpec STOP_REASONS = register(
            CatalogSpec.builder("stop-reasons", "md_stop_reason", "StopReason", "stopReason", "Lý do dừng máy")
                    .col(s("code", "Mã lý do", 10).req().imm().srch())
                    .col(s("name", "Tên lý do", 200).req().srch())
                    .col(e("category", "Nhóm", "PLANNED", "UNPLANNED", "TECHNICAL", "MATERIAL", "OTHER").req())
                    .col(b("countsAsPlannedDowntime", "Tính là dừng kế hoạch").def(false))
                    .col(b("requiresTechConfirm", "Cần xác nhận kỹ thuật").def(false))
                    .col(b("canCreateRepairRequest", "Cho tạo yêu cầu sửa chữa").def(false))
                    .col(i("sortOrder", "Thứ tự").def(0))
                    .keys("code").writers("SYS_ADMIN").approvers("DEPUTY_DIRECTOR")
                    .build());

    public static final CatalogSpec REASON_CODES = register(
            CatalogSpec.builder("reason-codes", "md_reason_code", "ReasonCode", "reasonCode", "Danh mục lý do khác")
                    .col(e("groupCode", "Nhóm", "REPAIR_CAUSE", "FAILURE_CAUSE", "SPARE_ISSUE", "STAMP_WASTE",
                            "MATERIAL_VARIANCE", "ALERT_CAUSE", "ORDER_ADJUST", "PLAN_VARIANCE",
                            "NC_DISPOSITION", "PART_DISPOSITION").req().imm())
                    .col(s("code", "Mã lý do", 50).req().imm().srch())
                    .col(s("name", "Tên lý do", 200).req().srch())
                    .keys("groupCode", "code").writers("SYS_ADMIN").approvers("DEPUTY_DIRECTOR")
                    .build());

    public static final CatalogSpec DEFECTS = register(
            CatalogSpec.builder("defects", "md_defect_def", "DefectDef", "defect", "Danh mục lỗi chất lượng")
                    .col(s("code", "Mã lỗi", 50).req().imm().srch())
                    .col(s("name", "Tên lỗi", 200).req().srch())
                    .col(e("appliesTo", "Áp dụng cho", "ROD", "PACK", "CARTON", "MATERIAL", "PACKAGING").req())
                    .col(s("formGroup", "Nhóm biểu mẫu", 20))
                    .col(i("sortOrder", "Thứ tự").def(0))
                    .keys("code").writers("SYS_ADMIN", "QA_HEAD").approvers("QA_HEAD")
                    .usage("Luật phân cấp", "SELECT COUNT(*) FROM md_defect_grade_rule r JOIN md_defect_def d "
                            + "ON d.code = r.defect_code WHERE d.id = UUID_TO_BIN(?)")
                    .build());

    public static final CatalogSpec DEFECT_RULES = register(
            CatalogSpec.builder("defect-grade-rules", "md_defect_grade_rule", "DefectGradeRule", "defectRule",
                            "Luật phân cấp lỗi A/B/C")
                    .col(cref("defectCode", "Mã lỗi", "md_defect_def"))
                    .col(e("appliesTo", "Áp dụng cho", "ROD", "PACK", "CARTON", "MATERIAL", "PACKAGING").req())
                    .col(e("grade", "Cấp lỗi", "A", "B", "C").req())
                    .col(e("metric", "Cách đo", "COUNT_IN_SAMPLE", "RATE_PCT").req())
                    .col(d("thresholdValue", "Ngưỡng").req())
                    .col(i("sampleSize", "Cỡ mẫu"))
                    .col(dt("effectiveFrom", "Hiệu lực từ").req())
                    .col(dt("effectiveTo", "Hiệu lực đến"))
                    .keys("defectCode", "appliesTo", "grade", "effectiveFrom")
                    .writers("SYS_ADMIN", "QA_HEAD").approvers("QA_HEAD")
                    .build());

    public static final CatalogSpec QC_FORMS = register(
            CatalogSpec.builder("qc-form-templates", "md_qc_form_template", "QcFormTemplate", "qcForm",
                            "Biểu mẫu KSCL/ĐBCL")
                    .col(s("code", "Mã biểu mẫu", 30).req().imm().srch())
                    .col(s("formGroup", "Nhóm biểu mẫu", 20).req())
                    .col(s("name", "Tên biểu mẫu", 200).req().srch())
                    .col(e("category", "Loại", "INLINE", "QA").req())
                    .col(e("appliesToMachineType", "Áp dụng cho máy", "MAKER", "PACKER", "ANY").def("ANY"))
                    .col(i("frequencyMin", "Tần suất (phút)"))
                    .col(j("formSchema", "Lược đồ biểu mẫu (JSON)").req())
                    .col(i("versionNo", "Phiên bản").req().imm().def(1))
                    .col(e("status", "Trạng thái", "DRAFT", "ACTIVE", "RETIRED").def("DRAFT"))
                    .col(dt("effectiveFrom", "Hiệu lực từ"))
                    .col(dt("effectiveTo", "Hiệu lực đến"))
                    .keys("code", "versionNo").versioned().noActive()
                    .writers("SYS_ADMIN", "QA_HEAD").approvers("QA_HEAD")
                    .build());

    public static final CatalogSpec MAINT_TASKS = register(
            CatalogSpec.builder("maint-task-templates", "md_maint_task_template", "MaintTaskTemplate", "maintTask",
                            "Công việc bảo trì chuẩn")
                    .col(s("code", "Mã công việc", 50).req().imm().srch())
                    .col(s("name", "Tên công việc", 200).req().srch())
                    .col(e("targetType", "Đối tượng", "MODEL", "MACHINE_GROUP", "MACHINE").req())
                    .col(s("targetValue", "Giá trị đối tượng", 100).req())
                    .col(e("maintType", "Loại bảo trì", "CLEANING", "LUBRICATION", "INSPECTION", "PERIODIC",
                            "OTHER").req())
                    .col(i("cycleValue", "Chu kỳ").req())
                    .col(e("cycleUnit", "Đơn vị chu kỳ", "DAY", "WEEK", "MONTH", "OP_HOURS").req())
                    .col(i("estimatedMinutes", "Thời lượng ước tính (phút)"))
                    .col(e("skillRequired", "Kỹ năng", "MECHANICAL", "ELECTRICAL", "ANY").def("ANY"))
                    .col(s("executorUnit", "Đơn vị thực hiện", 50))
                    .col(e("priority", "Ưu tiên", "CRITICAL", "NORMAL").def("NORMAL"))
                    .col(j("checklist", "Checklist (JSON)"))
                    .col(j("parts", "Vật tư dự kiến (JSON)"))
                    .col(i("versionNo", "Phiên bản").req().imm().def(1))
                    .col(e("status", "Trạng thái", "DRAFT", "ACTIVE", "RETIRED").def("DRAFT"))
                    .col(dt("effectiveFrom", "Hiệu lực từ"))
                    .col(dt("effectiveTo", "Hiệu lực đến"))
                    .keys("code", "versionNo").versioned().noActive()
                    .writers("SYS_ADMIN", "MNT_TECH", "KTCD").approvers("KTCD")
                    .build());

    public static final CatalogSpec OVERHAUL_TYPES = register(
            CatalogSpec.builder("overhaul-types", "md_overhaul_type_def", "OverhaulTypeDef", "overhaulType",
                            "Hình thức bảo trì/trung tu/đại tu")
                    .col(s("code", "Mã", 10).req().imm().srch())
                    .col(s("name", "Tên", 100).req())
                    .keys("code").writers("SYS_ADMIN")
                    .build());

    public static final CatalogSpec OVERHAUL_FORMS = register(
            CatalogSpec.builder("overhaul-forms", "md_overhaul_form_template", "OverhaulFormTemplate",
                            "overhaulForm", "Biểu mẫu đánh giá/nghiệm thu TDT")
                    .col(s("code", "Mã biểu mẫu", 50).req().imm().srch())
                    .col(e("kind", "Loại", "ASSESS_MECH", "ASSESS_ELEC", "ACCEPTANCE").req())
                    .col(j("formSchema", "Lược đồ biểu mẫu (JSON)").req())
                    .col(i("versionNo", "Phiên bản").req().imm().def(1))
                    .col(e("status", "Trạng thái", "DRAFT", "ACTIVE", "RETIRED").def("DRAFT"))
                    .col(dt("effectiveFrom", "Hiệu lực từ"))
                    .col(dt("effectiveTo", "Hiệu lực đến"))
                    .keys("code", "versionNo").versioned().noActive()
                    .writers("SYS_ADMIN", "KTCD").approvers("KTCD")
                    .build());

    public static final CatalogSpec SUPPLIERS = register(
            CatalogSpec.builder("suppliers", "md_supplier", "Supplier", "supplier", "Nhà cung cấp")
                    .col(s("code", "Mã NCC", 50).req().imm().srch())
                    .col(s("name", "Tên NCC", 200).req().srch())
                    .col(s("contact", "Liên hệ", 200))
                    .keys("code").writers("SYS_ADMIN", "WAREHOUSE_KEEPER")
                    .usage("Phụ tùng", "SELECT COUNT(*) FROM md_spare_part WHERE default_supplier_id = UUID_TO_BIN(?)")
                    .build());

    public static final CatalogSpec SPARE_PARTS = register(
            CatalogSpec.builder("spare-parts", "md_spare_part", "SparePart", "sparePart", "Phụ tùng & thẻ kho")
                    .col(s("partNo", "Mã hàng", 50).req().imm().srch())
                    .col(s("name", "Tên phụ tùng", 200).req().srch())
                    .col(s("uom", "Đơn vị tính", 20).req())
                    .col(s("stockCardNo", "Thẻ kho", 50).srch())
                    .col(s("groupCode", "Nhóm", 50))
                    .col(ref("defaultSupplierId", "Nhà cung cấp mặc định", "md_supplier", "code"))
                    .col(s("erpCode", "Mã ERP", 50))
                    .keys("partNo").writers("SYS_ADMIN", "WAREHOUSE_KEEPER", "MNT_TECH", "KTCD")
                    .approvers("KTCD")
                    .usage("Mức tồn tối thiểu", "SELECT COUNT(*) FROM md_spare_part_min_stock "
                            + "WHERE spare_part_id = UUID_TO_BIN(?)")
                    .usage("Liên kết thiết bị", "SELECT COUNT(*) FROM md_machine_part_link "
                            + "WHERE spare_part_id = UUID_TO_BIN(?)")
                    .build());

    public static final CatalogSpec MIN_STOCK = register(
            CatalogSpec.builder("spare-part-min-stock", "md_spare_part_min_stock", "SparePartMinStock", "minStock",
                            "Mức tồn tối thiểu phụ tùng")
                    .col(ref("sparePartId", "Phụ tùng", "md_spare_part", "part_no").req().imm())
                    .col(d("minQty", "Tồn tối thiểu").req())
                    .col(d("reorderQty", "Số lượng đặt lại"))
                    .col(i("leadTimeDays", "Thời gian cung ứng (ngày)"))
                    .col(dt("effectiveFrom", "Hiệu lực từ").req().imm())
                    .col(dt("effectiveTo", "Hiệu lực đến"))
                    .col(s("changeReason", "Lý do thay đổi", 500))
                    .keys("sparePartId", "effectiveFrom").noActive()
                    .writers("SYS_ADMIN", "WAREHOUSE_KEEPER", "MNT_TECH", "KTCD").approvers("KTCD")
                    .build());

    public static final CatalogSpec PART_LINKS = register(
            CatalogSpec.builder("machine-part-links", "md_machine_part_link", "MachinePartLink", "partLink",
                            "Liên kết thiết bị – phụ tùng")
                    .col(ref("machineId", "Máy", "md_machine", "code"))
                    .col(s("modelCode", "Dòng máy", 50))
                    .col(ref("componentId", "Cụm", "md_machine_component", "code"))
                    .col(ref("taskTemplateId", "Công việc bảo trì", "md_maint_task_template", "code"))
                    .col(ref("sparePartId", "Phụ tùng", "md_spare_part", "part_no").req())
                    .col(d("typicalQty", "Số lượng thường dùng"))
                    .keys("machineId", "modelCode", "componentId", "taskTemplateId", "sparePartId").noActive()
                    .writers("SYS_ADMIN", "MNT_TECH", "KTCD", "WAREHOUSE_KEEPER").approvers("KTCD")
                    .build());

    public static final CatalogSpec THRESHOLDS = register(
            CatalogSpec.builder("thresholds", "md_threshold", "Threshold", "threshold", "Ngưỡng cảnh báo")
                    .col(s("metricCode", "Mã chỉ số", 100).req().srch())
                    .col(e("scopeType", "Phạm vi", "GLOBAL", "MODEL", "MACHINE", "PRODUCT", "MATERIAL_GROUP",
                            "STAMP_TYPE").req().def("GLOBAL"))
                    .col(u("scopeId", "Id đối tượng (máy/sản phẩm)"))
                    .col(s("scopeValue", "Giá trị phạm vi (dòng máy/nhóm NVL/loại tem)", 100))
                    .col(e("level", "Mức", "INFO", "WARN", "CRIT", "A", "B", "C"))
                    .col(d("thresholdValue", "Giá trị").req())
                    .col(s("unit", "Đơn vị", 20))
                    .col(dt("effectiveFrom", "Hiệu lực từ").req())
                    .col(dt("effectiveTo", "Hiệu lực đến"))
                    .col(e("status", "Trạng thái", "DRAFT", "ACTIVE", "RETIRED").def("ACTIVE"))
                    .col(s("changeReason", "Lý do thay đổi", 500))
                    .keys("metricCode", "scopeType", "scopeId", "scopeValue", "level", "effectiveFrom").noActive()
                    .writers("SYS_ADMIN", "DEPUTY_DIRECTOR").approvers("DEPUTY_DIRECTOR")
                    .build());

    private Catalogs() {
    }

    public static List<CatalogSpec> all() {
        return List.copyOf(BY_RESOURCE.values());
    }

    public static CatalogSpec byResource(String resource) {
        return BY_RESOURCE.get(resource);
    }

    private static CatalogSpec register(CatalogSpec spec) {
        BY_RESOURCE.put(spec.resource(), spec);
        return spec;
    }

    // --- Khai báo cột ---

    private static Col s(String name, String label, int len) {
        return new Col(name, label, Type.STRING, false, len, List.of(), null, null, false, false, null);
    }

    private static Col i(String name, String label) {
        return new Col(name, label, Type.INT, false, 0, List.of(), null, null, false, false, null);
    }

    private static Col d(String name, String label) {
        return new Col(name, label, Type.DECIMAL, false, 0, List.of(), null, null, false, false, null);
    }

    private static Col b(String name, String label) {
        return new Col(name, label, Type.BOOL, false, 0, List.of(), null, null, false, false, null);
    }

    private static Col dt(String name, String label) {
        return new Col(name, label, Type.DATE, false, 0, List.of(), null, null, false, false, null);
    }

    private static Col tm(String name, String label) {
        return new Col(name, label, Type.TIME, false, 0, List.of(), null, null, false, false, null);
    }

    private static Col e(String name, String label, String... values) {
        return new Col(name, label, Type.ENUM, false, 30, List.of(values), null, null, false, false, null);
    }

    private static Col ref(String name, String label, String table, String refKey) {
        return new Col(name, label, Type.REF, false, 0, List.of(), table, refKey, false, false, null);
    }

    private static Col cref(String name, String label, String table) {
        return new Col(name, label, Type.CODE_REF, false, 50, List.of(), table, "code", false, false, null);
    }

    private static Col j(String name, String label) {
        return new Col(name, label, Type.JSON, false, 0, List.of(), null, null, false, false, null);
    }

    private static Col u(String name, String label) {
        return new Col(name, label, Type.UUID, false, 0, List.of(), null, null, false, false, null);
    }
}
