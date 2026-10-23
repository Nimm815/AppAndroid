package vn.edu.taskmind.auth.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AvatarStorageTest {
    @TempDir Path directory;

    @Test void storesValidatedImageAndReturnsOnlyAnOpaqueReference() throws Exception {
        AvatarStorage storage = new AvatarStorage(directory.toString());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB), "png", output);
        String reference = storage.store(new MockMultipartFile(
                "file", "avatar.png", "image/png", output.toByteArray()));

        assertTrue(reference.matches("[0-9a-f-]{36}\\.jpg"));
        assertTrue(storage.read(reference).length > 0);
        assertThrows(ResponseStatusException.class, () -> storage.read("..\\avatar.jpg"));
    }

    @Test void rejectsUnsupportedOrInvalidImageUploads() {
        AvatarStorage storage = new AvatarStorage(directory.toString());
        assertThrows(ResponseStatusException.class, () -> storage.store(new MockMultipartFile(
                "file", "avatar.gif", "image/gif", new byte[]{1, 2, 3})));
        assertThrows(ResponseStatusException.class, () -> storage.store(new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[]{1, 2, 3})));
    }
}
