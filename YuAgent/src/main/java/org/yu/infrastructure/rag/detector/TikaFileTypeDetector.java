package org.yu.infrastructure.rag.detector;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author shilong.zang
 * @date 11:32 <br/>
 */
public class TikaFileTypeDetector {

    private static final Logger log = LoggerFactory.getLogger(TikaFileTypeDetector.class);
    private static final String UNKNOWN_FILE_TYPE = "未知类型";
    private static final Tika TIKA = new Tika();

    private TikaFileTypeDetector() {
    }

    public static String detectFileType(byte[] data) {
        if (data == null || data.length == 0) {
            return UNKNOWN_FILE_TYPE;
        }

        try {
            return TIKA.detect(new ByteArrayInputStream(data));
        } catch (IOException e) {
            log.warn("文件类型检测失败: {}", e.getMessage());
            return UNKNOWN_FILE_TYPE;
        }
    }

}
