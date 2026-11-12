package org.bike.fx.workorder;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;

/** Fills the acceptance-act DOCX from values already present in the work-order form. */
public final class AcceptanceActPrinter {
    private static final String TEMPLATE_NAME = "acceptance-template.docx";
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public record Line(String name, String quantity, String unitPrice, String total) {}

    public record Data(
            String date,
            String clientName,
            String clientPhone,
            String itemType,
            String itemNames,
            String issue,
            String clientComment,
            String total,
            String giverLabel,
            String receiverLabel,
            String orderNumber,
            List<Line> lines) {
        public Data {
            lines = List.copyOf(lines);
        }
    }

    public Path generate(Data data) throws IOException {
        Path outputDirectory =
                Path.of(System.getProperty("user.home"), "Documents", "CraftBiker", "Акты");
        Path output =
                outputDirectory.resolve(
                        "Акт-приёма-" + LocalDateTime.now().format(FILE_TIMESTAMP) + ".docx");
        return generate(data, output);
    }

    Path generate(Data data, Path output) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());

        Path templatePath = findTemplate();
        try (var template = Files.newInputStream(templatePath)) {
            try (XWPFDocument document = new XWPFDocument(template)) {
                replaceScalars(document, scalarValues(data));
                populateLines(document, data.lines());
                ActFooter.add(document, data.orderNumber());
                try (var outputStream = Files.newOutputStream(output)) {
                    document.write(outputStream);
                }
            }
        }
        return output;
    }

    private Path findTemplate() throws IOException {
        var candidates = new ArrayList<Path>();
        String configuredPath = System.getProperty("craftbiker.acceptanceTemplate");
        if (configuredPath != null && !configuredPath.isBlank())
            candidates.add(Path.of(configuredPath));

        try {
            var codeSource = getClass().getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                Path location = Path.of(codeSource.getLocation().toURI());
                Path applicationDirectory = Files.isDirectory(location) ? location : location.getParent();
                if (applicationDirectory != null)
                    candidates.add(applicationDirectory.resolve("templates").resolve(TEMPLATE_NAME));
            }
        } catch (java.net.URISyntaxException exception) {
            throw new IOException("Не удалось определить папку приложения для шаблона.", exception);
        }

        candidates.add(Path.of(System.getProperty("user.dir"), "templates", TEMPLATE_NAME));
        return candidates.stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IOException(
                        "Не найден внешний шаблон акта приёма: templates/" + TEMPLATE_NAME));
    }

    public void openForPrinting(Path document) throws IOException {
        if (!Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            throw new IOException("На компьютере не настроено приложение для открытия DOCX.");
        }
        Desktop.getDesktop().open(document.toFile());
    }

    private Map<String, String> scalarValues(Data data) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("[[DATE]]", data.date());
        values.put("[[CLIENT_NAME]]", data.clientName());
        values.put("[[CLIENT_PHONE]]", data.clientPhone());
        values.put("[[ITEM_TYPE]]", data.itemType());
        values.put("[[ITEM_NAMES]]", data.itemNames());
        values.put("[[ISSUE]]", data.issue());
        values.put("[[CLIENT_COMMENT]]", data.clientComment());
        values.put("[[TOTAL]]", data.total());
        values.put("[[GIVER_LABEL]]", data.giverLabel());
        values.put("[[RECEIVER_LABEL]]", data.receiverLabel());
        return values;
    }

    private void replaceScalars(XWPFDocument document, Map<String, String> values) {
        document.getParagraphs().forEach(paragraph -> replaceTokens(paragraph, values));
        document.getTables().forEach(table -> replaceTableTokens(table, values));
    }

    private void replaceTableTokens(XWPFTable table, Map<String, String> values) {
        for (XWPFTableRow row : table.getRows()) {
            row.getTableCells().forEach(
                    cell -> {
                        cell.getParagraphs().forEach(paragraph -> replaceTokens(paragraph, values));
                        cell.getTables().forEach(nested -> replaceTableTokens(nested, values));
                    });
        }
    }

    private void populateLines(XWPFDocument document, List<Line> lines) throws IOException {
        if (document.getTables().isEmpty()) throw new IOException("В шаблоне нет таблицы работ.");
        XWPFTable table = document.getTables().getFirst();
        if (table.getNumberOfRows() < 2) throw new IOException("В таблице работ нет строки-шаблона.");
        CTRow rowTemplate = (CTRow) table.getRow(1).getCtRow().copy();
        table.removeRow(1);

        List<Line> rows = lines.isEmpty()
                ? List.of(new Line("Работы и запчасти не указаны", "", "", ""))
                : lines;
        for (int index = 0; index < rows.size(); index++) {
            XWPFTableRow row = new XWPFTableRow((CTRow) rowTemplate.copy(), table);
            table.addRow(row);
            Line line = rows.get(index);
            Map<String, String> values = Map.of(
                    "[[ROW_NO]]", Integer.toString(index + 1),
                    "[[ROW_NAME]]", Objects.toString(line.name(), ""),
                    "[[ROW_QTY]]", Objects.toString(line.quantity(), ""),
                    "[[ROW_PRICE]]", Objects.toString(line.unitPrice(), ""),
                    "[[ROW_SUM]]", Objects.toString(line.total(), ""));
            row.getTableCells().forEach(
                    cell -> cell.getParagraphs().forEach(paragraph -> replaceTokens(paragraph, values)));
        }
    }

    private void replaceTokens(XWPFParagraph paragraph, Map<String, String> values) {
        for (XWPFRun run : new ArrayList<>(paragraph.getRuns())) {
            String text = run.text();
            if (text == null || text.isEmpty()) continue;
            String replaced = text;
            for (var entry : values.entrySet()) replaced = replaced.replace(entry.getKey(), entry.getValue());
            if (!replaced.equals(text)) setRunText(run, replaced);
        }
    }

    private void setRunText(XWPFRun run, String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        var content = run.getCTR();
        while (content.sizeOfTArray() > 0) content.removeT(content.sizeOfTArray() - 1);
        while (content.sizeOfBrArray() > 0) content.removeBr(content.sizeOfBrArray() - 1);
        while (content.sizeOfCrArray() > 0) content.removeCr(content.sizeOfCrArray() - 1);
        if (!lines[0].isEmpty()) run.setText(lines[0]);
        for (int index = 1; index < lines.length; index++) {
            run.addBreak();
            if (!lines[index].isEmpty()) run.setText(lines[index]);
        }
    }
}


