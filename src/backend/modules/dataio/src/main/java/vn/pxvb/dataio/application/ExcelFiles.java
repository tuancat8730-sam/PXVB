package vn.pxvb.dataio.application;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.dataio.contract.ImportTemplate;
import vn.pxvb.dataio.contract.ImportTemplate.Column;

/** Đọc/ghi Excel (.xlsx) cho nhập liệu: sheet "Hướng dẫn", "Dữ liệu", "Danh mục tham chiếu" (FR-CC-070). */
final class ExcelFiles {

    static final String DATA_SHEET = "Dữ liệu";
    static final String GUIDE_SHEET = "Hướng dẫn";
    static final String REFERENCE_SHEET = "Danh mục tham chiếu";
    static final int MAX_ROWS = 50_000;

    record ParsedRow(int rowNo, Map<String, String> values) {
    }

    record RowError(int row, String column, String message) {
    }

    private ExcelFiles() {
    }

    static boolean looksLikeXlsx(byte[] bytes) {
        return bytes != null && bytes.length > 4 && bytes[0] == 'P' && bytes[1] == 'K';
    }

    static List<ParsedRow> read(byte[] bytes, List<Column> columns) {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet(DATA_SHEET);
            if (sheet == null) {
                sheet = wb.getSheetAt(0);
            }
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE, "Tệp không có dòng tiêu đề");
            }
            Map<String, Integer> indexByKey = mapHeaders(header, columns);
            List<String> missing = columns.stream().filter(c -> c.required() && !indexByKey.containsKey(c.key()))
                    .map(Column::header).toList();
            if (!missing.isEmpty()) {
                throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE,
                        "Thiếu cột bắt buộc trong tệp: " + String.join(", ", missing));
            }
            List<ParsedRow> rows = new ArrayList<>();
            for (int r = header.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                boolean any = false;
                for (Column c : columns) {
                    Integer idx = indexByKey.get(c.key());
                    String v = idx == null ? "" : cellText(row.getCell(idx));
                    if (!v.isEmpty()) {
                        any = true;
                    }
                    values.put(c.key(), v);
                }
                if (any) {
                    rows.add(new ParsedRow(r + 1, values));
                    if (rows.size() > MAX_ROWS) {
                        throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE,
                                "Tệp vượt quá " + MAX_ROWS + " dòng, vui lòng chia nhỏ");
                    }
                }
            }
            return rows;
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE, "Không đọc được tệp Excel (.xlsx)");
        }
    }

    private static Map<String, Integer> mapHeaders(Row header, List<Column> columns) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Cell cell : header) {
            String text = cellText(cell).toLowerCase(Locale.ROOT);
            for (Column c : columns) {
                if (text.equals(c.header().toLowerCase(Locale.ROOT)) || text.equals(c.key().toLowerCase(Locale.ROOT))) {
                    result.putIfAbsent(c.key(), cell.getColumnIndex());
                }
            }
        }
        return result;
    }

    static String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                yield BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            }
            case FORMULA -> switch (cell.getCachedFormulaResultType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                default -> "";
            };
            default -> "";
        };
    }

    static byte[] template(ImportTemplate template) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = boldStyle(wb);
            Sheet guide = wb.createSheet(GUIDE_SHEET);
            guide.createRow(0).createCell(0).setCellValue(template.title());
            guide.getRow(0).getCell(0).setCellStyle(bold);
            Row head = guide.createRow(2);
            String[] titles = {"Cột", "Bắt buộc", "Hướng dẫn"};
            for (int i = 0; i < titles.length; i++) {
                head.createCell(i).setCellValue(titles[i]);
                head.getCell(i).setCellStyle(bold);
            }
            int r = 3;
            for (Column c : template.columns()) {
                Row row = guide.createRow(r++);
                row.createCell(0).setCellValue(c.header());
                row.createCell(1).setCellValue(c.required() ? "Có" : "Không");
                row.createCell(2).setCellValue(c.hint() == null ? "" : c.hint());
            }
            guide.setColumnWidth(0, 30 * 256);
            guide.setColumnWidth(2, 70 * 256);

            Sheet data = wb.createSheet(DATA_SHEET);
            Row header = data.createRow(0);
            int i = 0;
            for (Column c : template.columns()) {
                header.createCell(i).setCellValue(c.header());
                header.getCell(i).setCellStyle(bold);
                data.setColumnWidth(i, 24 * 256);
                i++;
            }
            data.createFreezePane(0, 1);

            Map<String, List<String>> refs = template.references();
            Sheet ref = wb.createSheet(REFERENCE_SHEET);
            int col = 0;
            for (Map.Entry<String, List<String>> e : refs.entrySet()) {
                Row h = ref.getRow(0) == null ? ref.createRow(0) : ref.getRow(0);
                h.createCell(col).setCellValue(e.getKey());
                h.getCell(col).setCellStyle(bold);
                int rr = 1;
                for (String v : e.getValue()) {
                    Row row = ref.getRow(rr) == null ? ref.createRow(rr) : ref.getRow(rr);
                    row.createCell(col).setCellValue(v);
                    rr++;
                }
                ref.setColumnWidth(col, 28 * 256);
                col++;
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] errorReport(List<RowError> errors) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Lỗi");
            CellStyle bold = boldStyle(wb);
            String[] titles = {"Dòng", "Cột", "Lỗi"};
            Row head = sheet.createRow(0);
            for (int i = 0; i < titles.length; i++) {
                head.createCell(i).setCellValue(titles[i]);
                head.getCell(i).setCellStyle(bold);
            }
            int r = 1;
            for (RowError e : errors) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(e.row());
                row.createCell(1).setCellValue(e.column() == null ? "" : e.column());
                row.createCell(2).setCellValue(e.message());
            }
            sheet.setColumnWidth(1, 28 * 256);
            sheet.setColumnWidth(2, 80 * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static CellStyle boldStyle(Workbook wb) {
        Font font = wb.createFont();
        font.setBold(true);
        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        return style;
    }
}
