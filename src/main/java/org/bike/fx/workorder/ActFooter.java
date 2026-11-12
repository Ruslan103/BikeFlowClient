package org.bike.fx.workorder;

import java.math.BigInteger;
import java.util.Objects;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabStop;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabs;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc;

final class ActFooter {
    private static final BigInteger FALLBACK_TAB_POSITION = BigInteger.valueOf(9000);

    private ActFooter() {}

    static void add(XWPFDocument document, String orderNumber) {
        XWPFFooter footer = document.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph paragraph = footer.createParagraph();
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        addRightTabStop(paragraph, usableWidth(document));

        XWPFRun label = paragraph.createRun();
        label.setFontSize(9);
        label.setText("Бланк № " + Objects.toString(orderNumber, ""));
        paragraph.createRun().addTab();
        addPageNumber(paragraph);
    }

    private static void addRightTabStop(XWPFParagraph paragraph, BigInteger position) {
        CTPPr properties = paragraph.getCTP().isSetPPr()
                ? paragraph.getCTP().getPPr()
                : paragraph.getCTP().addNewPPr();
        CTTabs tabs = properties.isSetTabs() ? properties.getTabs() : properties.addNewTabs();
        CTTabStop tab = tabs.addNewTab();
        tab.setVal(STTabJc.RIGHT);
        tab.setPos(position);
    }

    private static BigInteger usableWidth(XWPFDocument document) {
        var section = document.getDocument().getBody().getSectPr();
        if (section == null || !section.isSetPgSz() || !section.isSetPgMar()) return FALLBACK_TAB_POSITION;
        BigInteger width = asBigInteger(section.getPgSz().getW());
        BigInteger left = asBigInteger(section.getPgMar().getLeft());
        BigInteger right = asBigInteger(section.getPgMar().getRight());
        if (width == null || left == null || right == null) return FALLBACK_TAB_POSITION;
        BigInteger usable = width.subtract(left).subtract(right);
        return usable.signum() > 0 ? usable : FALLBACK_TAB_POSITION;
    }

    private static BigInteger asBigInteger(Object value) {
        if (value instanceof BigInteger integer) return integer;
        if (value instanceof Number number) return BigInteger.valueOf(number.longValue());
        return null;
    }

    private static void addPageNumber(XWPFParagraph paragraph) {
        XWPFRun begin = paragraph.createRun();
        begin.getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
        XWPFRun instruction = paragraph.createRun();
        instruction.getCTR().addNewInstrText().setStringValue(" PAGE ");
        XWPFRun separator = paragraph.createRun();
        separator.getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
        XWPFRun result = paragraph.createRun();
        result.setText("1");
        XWPFRun end = paragraph.createRun();
        end.getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
        for (XWPFRun run : paragraph.getRuns()) run.setFontSize(9);
    }
}


