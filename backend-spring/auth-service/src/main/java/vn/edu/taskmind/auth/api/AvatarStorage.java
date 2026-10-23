package vn.edu.taskmind.auth.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.UUID;

@Component
public class AvatarStorage {
    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 4096;
    private final Path directory;

    public AvatarStorage(@Value("${taskmind.avatar.directory:build/auth-avatars}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public String store(MultipartFile upload) {
        if (upload == null || upload.isEmpty() || upload.getSize() > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh đại diện phải có dung lượng từ 1 byte đến 5 MB");
        }
        String mediaType = upload.getContentType();
        if (!"image/jpeg".equalsIgnoreCase(mediaType) && !"image/png".equalsIgnoreCase(mediaType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Chỉ chấp nhận ảnh JPEG hoặc PNG");
        }

        try {
            byte[] source = upload.getBytes();
            BufferedImage image = decodeBoundedImage(source);
            String reference = UUID.randomUUID() + ".jpg";
            Path target = resolve(reference);
            Files.createDirectories(directory);
            Files.write(target, toJpeg(image));
            return reference;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đọc được ảnh đại diện", e);
        }
    }

    public byte[] read(String reference) {
        try {
            return Files.readAllBytes(resolve(reference));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh đại diện", e);
        }
    }

    public void delete(String reference) {
        if (reference == null || reference.isBlank()) return;
        try {
            Files.deleteIfExists(resolve(reference));
        } catch (IOException e) {
            throw new IllegalStateException("Could not delete previous avatar", e);
        }
    }

    private Path resolve(String reference) {
        if (reference == null || !reference.matches("[0-9a-fA-F-]{36}\\.jpg")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh đại diện");
        }
        Path target = directory.resolve(reference).normalize();
        if (!target.getParent().equals(directory)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh đại diện");
        }
        return target;
    }

    private BufferedImage decodeBoundedImage(byte[] source) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(source))) {
            if (input == null) throw new IOException("Invalid image");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported image");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("jpeg") && !format.equalsIgnoreCase("png")) {
                    throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Chỉ chấp nhận ảnh JPEG hoặc PNG");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                        || (long) width * height > 12_000_000L) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kích thước ảnh vượt giới hạn cho phép");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw new IOException("Image decode failed");
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private byte[] toJpeg(BufferedImage source) throws IOException {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(rgb, "jpeg", output)) throw new IOException("JPEG encoder is unavailable");
            if (output.size() > MAX_UPLOAD_BYTES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh sau khi xử lý vượt quá 5 MB");
            }
            return output.toByteArray();
        }
    }
}
