package org.yu.infrastructure.rag.detector;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TikaFileTypeDetectorTest {

    @Test
    void shouldIdentifyPngContentAndHandleEmptyContent() {
        byte[] pngHeader = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

        assertEquals("image/png", TikaFileTypeDetector.detectFileType(pngHeader));
        assertEquals("未知类型", TikaFileTypeDetector.detectFileType(null));
        assertEquals("未知类型", TikaFileTypeDetector.detectFileType(new byte[0]));
    }
}
