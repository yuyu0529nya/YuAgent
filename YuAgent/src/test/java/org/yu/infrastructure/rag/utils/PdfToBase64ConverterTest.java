package org.yu.infrastructure.rag.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

class PdfToBase64ConverterTest {

    @Test
    void openSession_shouldReuseDocumentAndRenderPage() throws Exception {
        byte[] pdfBytes = createSinglePagePdf();

        try (PdfToBase64Converter.PdfPageImageSession session = PdfToBase64Converter.openSession(pdfBytes)) {
            assertEquals(1, session.getPageCount());

            String base64 = session.renderPageToBase64(0, "jpg", 120);
            assertFalse(base64.isBlank());
        }
    }

    private byte[] createSinglePagePdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return out.toByteArray();
        }
    }
}
