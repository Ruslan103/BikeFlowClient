package org.bike.fx.workorder;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;

/** Fills the external issue-act DOCX template with the current work-order values. */
public final class IssueActPrinter {
    private static final String TEMPLATE_NAME = "issue-template.docx";
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM", Locale.forLanguageTag("ru-RU"));

    public record Data(
            String title,
            String number,
            LocalDate date,
            String clientName,
            String clientPhone,
            String itemLabel,
            String itemDescription,
            String acceptedSentence,
            String total,
            List<AcceptanceActPrinter.Line> lines) {
        public Data {
            lines = List.copyOf(lines);
        }
    }

    public Path generate(Data data) throws IOException {
        Path outputDirectory = Path.of(System.getProperty("user.home"), "Documents", "CraftBiker", "Акты");
        Path output = outputDirectory.resolve("Акт-выдачи-" + LocalDateTime.now().format(FILE_TIMESTAMP) + ".docx");
        return generate(data, output);
    }

    Path generate(Data data, Path output) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        Path templatePath = findTemplate();
        try (var input = Files.newInputStream(templatePath);
                var document = new XWPFDocument(input)) {
            fillHeader(document, data);
            fillCustomerDetails(document, data);
            fillSignature(document);
            populateLines(document, data.lines(), data.total());
            ActFooter.add(document, data.number());
            try (var outputStream = Files.newOutputStream(output)) {
                document.write(outputStream);
            }
        }
        return output;
    }

    private Path findTemplate() throws IOException {
        var candidates = new ArrayList<Path>();
        String configuredPath = System.getProperty("craftbiker.issueTemplate");
        if (configuredPath != null && !configuredPath.isBlank()) candidates.add(Path.of(configuredPath));
        try {
            var codeSource = getClass().getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                Path location = Path.of(codeSource.getLocation().toURI());
                Path applicationDirectory = Files.isDirectory(location) ? location : location.getParent();
                if (applicationDirectory != null) candidates.add(applicationDirectory.resolve("templates").resolve(TEMPLATE_NAME));
            }
        } catch (java.net.URISyntaxException exception) {
            throw new IOException("Не удалось определить папку приложения для шаблона акта выдачи.", exception);
        }
        candidates.add(Path.of(System.getProperty("user.dir"), "templates", TEMPLATE_NAME));
        return candidates.stream().filter(Files::isRegularFile).findFirst()
                .orElseThrow(() -> new IOException("Не найден внешний шаблон акта выдачи: templates/" + TEMPLATE_NAME));
    }

    private void fillHeader(XWPFDocument document, Data data) throws IOException {
        XWPFParagraph paragraph = findParagraph(document, "АКТ ВЫДАЧИ");
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.size() < 5) throw new IOException("В шаблоне акта выдачи не найдены поля номера и даты.");
        setRun(runs.get(0), data.title() + " № ");
        setRun(runs.get(1), data.number());
        setRun(runs.get(2), " от «" + data.date().getDayOfMonth() + "» ");
        setRun(runs.get(3), data.date().format(MONTH));
        setRun(runs.get(4), " " + data.date().getYear() + " г.");
    }

    private void fillCustomerDetails(XWPFDocument document, Data data) throws IOException {
        XWPFParagraph paragraph = findParagraph(document, "Клиент:");
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.size() < 9) throw new IOException("В шаблоне акта выдачи не найдены поля клиента и принятого имущества.");
        setRun(runs.get(1), data.clientName());
        setRun(runs.get(4), data.clientPhone());
        setRun(runs.get(5), data.itemLabel() + " " + data.itemDescription());
        setRun(runs.get(6), "Выполненные работы и использованные запчасти указаны в таблице.");
        for (XWPFRun run : runs) {
            String text = run.text();
            if (text != null && text.contains("Клиент принял велосипед.")) {
                setRun(run, data.acceptedSentence());
                return;
            }
        }
        throw new IOException("В шаблоне акта выдачи не найдена строка подтверждения приёма.");
    }

    private void fillSignature(XWPFDocument document) throws IOException {
        XWPFParagraph paragraph = findParagraph(document, "Мастер (сдал):");
        if (paragraph.getRuns().isEmpty()) throw new IOException("В шаблоне акта выдачи не найдены подписи.");
        setRun(paragraph.getRuns().getFirst(), "Мастер (выдал): ");
    }

    private void populateLines(XWPFDocument document, List<AcceptanceActPrinter.Line> lines, String total)
            throws IOException {
        if (document.getTables().isEmpty()) throw new IOException("В шаблоне акта выдачи нет таблицы работ и запчастей.");
        XWPFTable table = document.getTables().getFirst();
        if (table.getNumberOfRows() < 3) throw new IOException("В таблице акта выдачи отсутствуют строки данных и итога.");
        CTRow rowTemplate = (CTRow) table.getRow(1).getCtRow().copy();
        for (int index = table.getNumberOfRows() - 2; index >= 1; index--) table.removeRow(index);

        List<AcceptanceActPrinter.Line> rows = lines.isEmpty()
                ? List.of(new AcceptanceActPrinter.Line("Работы и товары не указаны", "", "", ""))
                : lines;
        for (int index = 0; index < rows.size(); index++) {
            XWPFTableRow row = new XWPFTableRow((CTRow) rowTemplate.copy(), table);
            table.addRow(row, table.getNumberOfRows() - 1);
            AcceptanceActPrinter.Line line = rows.get(index);
            setCell(row, 0, Integer.toString(index + 1));
            setCell(row, 1, line.name());
            setCell(row, 2, line.quantity());
            setCell(row, 3, line.unitPrice());
            setCell(row, 4, line.total());
        }
        XWPFTableRow totalRow = table.getRow(table.getNumberOfRows() - 1);
        if (totalRow.getTableCells().size() < 2) throw new IOException("В таблице акта выдачи не найдена ячейка итоговой суммы.");
        setCell(totalRow, 1, total);
    }

    private void setCell(XWPFTableRow row, int cellIndex, String value) throws IOException {
        if (cellIndex >= row.getTableCells().size()) throw new IOException("В строке шаблона акта выдачи не хватает колонок.");
        var paragraphs = row.getCell(cellIndex).getParagraphs();
        XWPFParagraph paragraph = paragraphs.isEmpty() ? row.getCell(cellIndex).addParagraph() : paragraphs.getFirst();
        List<XWPFRun> runs = paragraph.getRuns();
        XWPFRun first = runs.isEmpty() ? paragraph.createRun() : runs.getFirst();
        setRun(first, value);
        for (int index = 1; index < runs.size(); index++) setRun(runs.get(index), "");
    }

    private XWPFParagraph findParagraph(XWPFDocument document, String text) throws IOException {
        for (XWPFParagraph paragraph : paragraphs(document)) {
            if (paragraph.getText().contains(text)) return paragraph;
        }
        throw new IOException("В шаблоне акта выдачи не найден текст: " + text);
    }

    private List<XWPFParagraph> paragraphs(XWPFDocument document) {
        var paragraphs = new ArrayList<XWPFParagraph>(document.getParagraphs());
        document.getTables().forEach(table -> addTableParagraphs(table, paragraphs));
        return paragraphs;
    }

    private void addTableParagraphs(XWPFTable table, List<XWPFParagraph> paragraphs) {
        table.getRows().forEach(row -> row.getTableCells().forEach(cell -> {
            paragraphs.addAll(cell.getParagraphs());
            cell.getTables().forEach(nested -> addTableParagraphs(nested, paragraphs));
        }));
    }

    private void setRun(XWPFRun run, String value) {
        run.setText(value == null ? "" : value, 0);
    }

    public void openForPrinting(Path document) throws IOException {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            throw new IOException("На компьютере не настроено приложение для открытия DOCX.");
        }
        Desktop.getDesktop().open(document.toFile());
    }
}





