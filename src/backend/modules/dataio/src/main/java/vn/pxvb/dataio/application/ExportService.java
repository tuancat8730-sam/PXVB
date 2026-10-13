package vn.pxvb.dataio.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Xuất Excel dữ liệu lớn bằng SXSSF (streaming, FR-CC-071). Số giữ dạng số; ngày giờ hiển thị
 * {@code dd/MM/yyyy HH:mm} múi giờ Asia/Ho_Chi_Minh; Boolean → "Có"/"Không".
 */
@Service
public class ExportService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] workbook(String sheetName, List<String> headers, Iterable<List<Object>> rows) {
        SXSSFWorkbook wb = new SXSSFWorkbook(200);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            SXSSFSheet sheet = wb.createSheet(sheetName);
            Font font = wb.createFont();
            font.setBold(true);
            CellStyle bold = wb.createCellStyle();
            bold.setFont(font);
            Row head = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                head.createCell(i).setCellValue(headers.get(i));
                head.getCell(i).setCellStyle(bold);
                sheet.setColumnWidth(i, 22 * 256);
            }
            int r = 1;
            for (List<Object> values : rows) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < values.size(); c++) {
                    write(row, c, values.get(c));
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Không tạo được tệp Excel", e);
        } finally {
            wb.dispose();
        }
    }

    private static void write(Row row, int col, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof BigDecimal d) {
            row.createCell(col).setCellValue(d.doubleValue());
        } else if (value instanceof Number n) {
            row.createCell(col).setCellValue(n.doubleValue());
        } else if (value instanceof Boolean b) {
            row.createCell(col).setCellValue(b ? "Có" : "Không");
        } else if (value instanceof LocalDate d) {
            row.createCell(col).setCellValue(DATE.format(d));
        } else if (value instanceof Instant i) {
            row.createCell(col).setCellValue(DATE_TIME.format(i.atZone(ZONE)));
        } else {
            row.createCell(col).setCellValue(String.valueOf(value));
        }
    }
}
