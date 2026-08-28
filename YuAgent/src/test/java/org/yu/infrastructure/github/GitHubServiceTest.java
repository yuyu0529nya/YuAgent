package org.yu.infrastructure.github;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yu.infrastructure.exception.BusinessException;

class GitHubServiceTest {

    @Test
    void shouldCopyArchiveWithinConfiguredLimit() throws Exception {
        byte[] content = "repository archive".getBytes();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        long copiedBytes = GitHubService.copyWithinLimit(new ByteArrayInputStream(content), output, content.length);

        assertEquals(content.length, copiedBytes);
        assertArrayEquals(content, output.toByteArray());
    }

    @Test
    void shouldRejectArchiveOverConfiguredLimit() {
        ByteArrayInputStream input = new ByteArrayInputStream("oversized".getBytes());

        assertThrows(IOException.class, () -> GitHubService.copyWithinLimit(input, new ByteArrayOutputStream(), 4));
    }

    @Test
    void shouldKeepTargetPathInsideRepository(@TempDir Path tempDirectory) {
        Path root = tempDirectory.resolve("repository");

        assertEquals(root.resolve("tools/weather").toAbsolutePath().normalize(),
                GitHubService.resolvePathWithinRepository(root, "tools/weather"));
        assertThrows(BusinessException.class, () -> GitHubService.resolvePathWithinRepository(root, "../outside"));
        assertThrows(BusinessException.class, () -> GitHubService.resolvePathWithinRepository(root, "."));
    }
}
