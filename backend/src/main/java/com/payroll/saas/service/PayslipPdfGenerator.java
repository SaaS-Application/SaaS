package com.payroll.saas.service;

import com.payroll.saas.dto.PayrollComponentResponse;
import com.payroll.saas.entity.SalaryComponentType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class PayslipPdfGenerator {

    private static final float MARGIN = 46;
    private static final float AMOUNT_RIGHT = PDRectangle.A4.getWidth() - MARGIN;
    private static final float NAME_WIDTH = AMOUNT_RIGHT - MARGIN - 112;
    private static final Color INK = new Color(31, 41, 55);
    private static final Color MUTED = new Color(91, 103, 120);
    private static final Color ACCENT = new Color(30, 64, 120);
    private static final Color RULE = new Color(211, 218, 228);
    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter PERIOD_FORMAT =
            DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH);

    public record PayslipData(
            String companyName,
            String companyAddress,
            String employeeName,
            String employeeNumber,
            String department,
            String designation,
            String payPeriod,
            LocalDate payrollDate,
            String payslipNumber,
            BigDecimal totalWorkingDays,
            BigDecimal paidDays,
            BigDecimal unpaidDays,
            BigDecimal grossEarnings,
            BigDecimal totalDeductions,
            BigDecimal netSalary,
            List<PayrollComponentResponse> components,
            boolean profileSnapshotAvailable) {
    }

    public byte[] generate(PayslipData data) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Layout layout = new Layout(document, data.payslipNumber(), data.payPeriod());
            layout.startPage();

            layout.wrapped(data.companyName(), BOLD, 16, INK, MARGIN, AMOUNT_RIGHT - MARGIN, 20);
            if (data.companyAddress() != null && !data.companyAddress().isBlank()) {
                layout.wrapped(data.companyAddress(), REGULAR, 9, MUTED, MARGIN, AMOUNT_RIGHT - MARGIN, 12);
            }
            layout.gap(12);
            layout.wrapped("PAYSLIP", BOLD, 21, ACCENT, MARGIN, AMOUNT_RIGHT - MARGIN, 25);
            layout.wrapped("Pay period: " + formatPeriod(data.payPeriod()), BOLD, 11, INK,
                    MARGIN, AMOUNT_RIGHT - MARGIN, 15);
            layout.wrapped("Payslip number: " + data.payslipNumber()
                            + "    Payroll date: " + DATE_FORMAT.format(data.payrollDate()),
                    REGULAR, 9, MUTED, MARGIN, AMOUNT_RIGHT - MARGIN, 13);
            layout.rule();

            layout.section("EMPLOYEE DETAILS");
            layout.wrapped("Employee: " + data.employeeName(), BOLD, 11, INK,
                    MARGIN, AMOUNT_RIGHT - MARGIN, 15);
            layout.wrapped("Employee number: " + data.employeeNumber(), REGULAR, 9, INK,
                    MARGIN, AMOUNT_RIGHT - MARGIN, 13);
            layout.wrapped("Department: " + valueOrUnavailable(data.department())
                            + "    Designation: " + valueOrUnavailable(data.designation()),
                    REGULAR, 9, INK, MARGIN, AMOUNT_RIGHT - MARGIN, 13);
            if (!data.profileSnapshotAvailable()) {
                layout.wrapped("Notice: company and employee profile details below reflect current records; "
                                + "payroll amounts and component values are from the saved payroll snapshot.",
                        REGULAR, 8, MUTED, MARGIN, AMOUNT_RIGHT - MARGIN, 11);
            }
            layout.gap(5);

            layout.section("ATTENDANCE SUMMARY");
            layout.wrapped("Total working days: " + dayValue(data.totalWorkingDays())
                            + "    Paid days: " + dayValue(data.paidDays())
                            + "    Unpaid / LOP days: " + dayValue(data.unpaidDays()),
                    REGULAR, 9, INK, MARGIN, AMOUNT_RIGHT - MARGIN, 13);
            layout.gap(5);

            drawComponents(layout, "EARNINGS", data.components(), SalaryComponentType.EARNING);
            drawComponents(layout, "DEDUCTIONS", data.components(), SalaryComponentType.DEDUCTION);

            layout.section("PAY SUMMARY");
            layout.keyValue("Gross earnings", currency(data.grossEarnings()));
            layout.keyValue("Total deductions", currency(data.totalDeductions()));
            layout.rule();
            layout.wrapped("NET SALARY    " + currency(data.netSalary()), BOLD, 15, ACCENT,
                    MARGIN, AMOUNT_RIGHT - MARGIN, 20);

            layout.finish();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate the payslip PDF", exception);
        }
    }

    private void drawComponents(
            Layout layout,
            String title,
            List<PayrollComponentResponse> components,
            SalaryComponentType type) throws IOException {
        List<PayrollComponentResponse> rows = components.stream()
                .filter(component -> component.type() == type)
                .toList();
        layout.section(title);
        layout.tableHeader();
        for (PayrollComponentResponse component : rows) {
            String label = component.name() + (component.code() == null || component.code().isBlank()
                    ? "" : " (" + component.code() + ")");
            List<String> lines = wrappedLines(label, REGULAR, 9, NAME_WIDTH);
            float rowHeight = Math.max(14, lines.size() * 12);
            if (!layout.hasSpace(rowHeight + 5)) {
                layout.nextPage();
                layout.section(title + " (continued)");
                layout.tableHeader();
            }
            float baseline = layout.currentY();
            for (String line : lines) {
                layout.textAt(line, REGULAR, 9, INK, MARGIN, baseline);
                baseline -= 12;
            }
            layout.rightText(currency(component.payableAmount()), REGULAR, 9, INK, AMOUNT_RIGHT, layout.currentY());
            layout.advance(rowHeight);
            layout.rule();
        }
        if (rows.isEmpty()) {
            layout.wrapped("No " + title.toLowerCase(Locale.ROOT) + " components", REGULAR, 9,
                    MUTED, MARGIN, AMOUNT_RIGHT - MARGIN, 13);
        }
        layout.gap(4);
    }

    private static String currency(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        format.setGroupingUsed(true);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        format.setRoundingMode(java.math.RoundingMode.HALF_UP);
        return "INR " + format.format(amount);
    }

    private static String dayValue(BigDecimal days) {
        return days == null ? "Not available" : days.stripTrailingZeros().toPlainString();
    }

    private static String valueOrUnavailable(String value) {
        return value == null || value.isBlank() ? "Not available" : value;
    }

    private static String formatPeriod(String value) {
        return YearMonth.parse(value).format(PERIOD_FORMAT);
    }

    private static List<String> wrappedLines(String value, PDFont font, float fontSize, float width)
            throws IOException {
        String safe = pdfSafe(value);
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : safe.split("\\s+")) {
            if (word.isEmpty()) continue;
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (textWidth(candidate, font, fontSize) <= width) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (textWidth(word, font, fontSize) <= width) {
                current.append(word);
            } else {
                for (int offset = 0; offset < word.length();) {
                    int end = offset + 1;
                    while (end <= word.length()
                            && textWidth(word.substring(offset, end), font, fontSize) <= width) {
                        end++;
                    }
                    int split = Math.max(offset + 1, end - 1);
                    lines.add(word.substring(offset, split));
                    offset = split;
                }
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines.isEmpty() ? List.of("") : lines;
    }

    private static float textWidth(String value, PDFont font, float fontSize) throws IOException {
        return font.getStringWidth(value) * fontSize / 1000;
    }

    private static String pdfSafe(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        StringBuilder safe = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint -> {
            if (codePoint >= 32 && codePoint <= 126) {
                safe.append((char) codePoint);
            } else {
                safe.append('?');
            }
        });
        return safe.toString();
    }

    private static final class Layout {
        private final PDDocument document;
        private final String payslipNumber;
        private final String payPeriod;
        private PDPageContentStream stream;
        private float y;
        private int pageNumber;

        private Layout(PDDocument document, String payslipNumber, String payPeriod) {
            this.document = document;
            this.payslipNumber = payslipNumber;
            this.payPeriod = payPeriod;
        }

        private void startPage() throws IOException {
            if (stream != null) {
                finishCurrentPage();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            pageNumber++;
            y = page.getMediaBox().getHeight() - 34;
            if (pageNumber > 1) {
                textAt("PAYSLIP " + payslipNumber + " | " + payPeriod, BOLD, 8, MUTED, MARGIN, y);
                y -= 18;
                rule();
            }
        }

        private void nextPage() throws IOException {
            startPage();
        }

        private void finish() throws IOException {
            finishCurrentPage();
        }

        private void finishCurrentPage() throws IOException {
            if (stream == null) return;
            textAt("PRIVATE AND CONFIDENTIAL", REGULAR, 7, MUTED, MARGIN, 24);
            rightText("Page " + pageNumber, REGULAR, 7, MUTED, AMOUNT_RIGHT, 24);
            stream.close();
            stream = null;
        }

        private void wrapped(String value, PDFont font, float size, Color color,
                             float x, float width, float leading) throws IOException {
            for (String line : wrappedLines(value, font, size, width)) {
                ensureSpace(leading);
                textAt(line, font, size, color, x, y);
                y -= leading;
            }
        }

        private void textAt(String value, PDFont font, float size, Color color, float x, float baseline)
                throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(color);
            stream.newLineAtOffset(x, baseline);
            stream.showText(pdfSafe(value));
            stream.endText();
        }

        private void rightText(String value, PDFont font, float size, Color color, float right, float baseline)
                throws IOException {
            String safe = pdfSafe(value);
            textAt(safe, font, size, color, right - textWidth(safe, font, size), baseline);
        }

        private void section(String value) throws IOException {
            ensureSpace(22);
            y -= 3;
            wrapped(value, BOLD, 10, ACCENT, MARGIN, AMOUNT_RIGHT - MARGIN, 14);
        }

        private void tableHeader() throws IOException {
            ensureSpace(17);
            textAt("Component", BOLD, 8, MUTED, MARGIN, y);
            rightText("Amount", BOLD, 8, MUTED, AMOUNT_RIGHT, y);
            y -= 5;
            rule();
            y -= 7;
        }

        private void keyValue(String label, String value) throws IOException {
            ensureSpace(17);
            textAt(label, REGULAR, 9, INK, MARGIN, y);
            rightText(value, REGULAR, 9, INK, AMOUNT_RIGHT, y);
            y -= 15;
        }

        private void rule() throws IOException {
            stream.setStrokingColor(RULE);
            stream.setLineWidth(0.6f);
            stream.moveTo(MARGIN, y);
            stream.lineTo(AMOUNT_RIGHT, y);
            stream.stroke();
            y -= 7;
        }

        private float currentY() {
            return y;
        }

        private void advance(float amount) {
            y -= amount;
        }

        private void gap(float amount) {
            y -= amount;
        }

        private boolean hasSpace(float amount) {
            return y - amount > 42;
        }

        private void ensureSpace(float amount) throws IOException {
            if (!hasSpace(amount)) {
                nextPage();
            }
        }
    }
}
