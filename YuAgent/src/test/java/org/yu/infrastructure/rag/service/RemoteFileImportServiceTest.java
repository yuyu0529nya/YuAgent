package org.yu.infrastructure.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.yu.infrastructure.exception.BusinessException;

class RemoteFileImportServiceTest {

    @Test
    void sanitizeAndValidate_shouldRejectLocalhost() throws Exception {
        RemoteFileImportService service = new RemoteFileImportService();
        Method method = RemoteFileImportService.class.getDeclaredMethod("sanitizeAndValidate", String.class);
        method.setAccessible(true);

        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> method.invoke(service, "http://127.0.0.1/demo.pdf"));

        assertTrue(error.getCause() instanceof BusinessException);
        assertTrue(error.getCause().getMessage() != null && !error.getCause().getMessage().isBlank());
    }

    @Test
    void buildIeeePdfUri_shouldConvertStampPageToPdfEndpoint() throws Exception {
        RemoteFileImportService service = new RemoteFileImportService();
        Method method = RemoteFileImportService.class.getDeclaredMethod("buildIeeePdfUri", URI.class);
        method.setAccessible(true);

        URI result = (URI) method.invoke(service,
                URI.create("https://ieeexplore.ieee.org/stamp/stamp.jsp?tp=&arnumber=10379000"));

        assertEquals("https://ieeexplore.ieee.org/stampPDF/getPDF.jsp?tp=&arnumber=10379000", result.toString());
    }

    @Test
    void ensureImportableBody_shouldRejectProtectedIeeeHtml() throws Exception {
        RemoteFileImportService service = new RemoteFileImportService();
        Method method = RemoteFileImportService.class.getDeclaredMethod("ensureImportableBody", URI.class, String.class,
                byte[].class);
        method.setAccessible(true);

        byte[] html = """
                <!DOCTYPE html><html><head></head><body>
                Please enable JavaScript to view the page content.
                Your support ID is: 123456.
                /TSPD/0807dc117eab2000
                </body></html>
                """.getBytes();

        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> method.invoke(service,
                        URI.create("https://ieeexplore.ieee.org/stampPDF/getPDF.jsp?tp=&arnumber=10379000"),
                        "text/html", html));

        assertTrue(error.getCause() instanceof BusinessException);
    }

    @Test
    void normalizeFilename_shouldPreferDetectedPdfExtension() throws Exception {
        RemoteFileImportService service = new RemoteFileImportService();
        Method method = RemoteFileImportService.class.getDeclaredMethod("normalizeFilename", String.class,
                String.class);
        method.setAccessible(true);

        String normalized = (String) method.invoke(service, "最新论文.html", "application/pdf");

        assertEquals("最新论文.pdf", normalized);
    }
}
