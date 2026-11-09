// AI-assisted image upload: generated filenames, bounded image dimensions and no active SVG
// content.
package cn.shiguang.shop.admin;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@RestController
public class ImageStorage implements WebMvcConfigurer {
  private final Path directory;

  public ImageStorage(@Value("${SHOP_UPLOAD_DIR:.local/uploads}") String directory)
      throws IOException {
    this.directory = Path.of(directory).toAbsolutePath().normalize();
    Files.createDirectories(this.directory);
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/uploads/**")
        .addResourceLocations(directory.toUri().toString())
        .setCacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1)));
  }

  @PostMapping(value = "/api/admin/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, String> upload(@RequestParam("file") MultipartFile file) throws IOException {
    if (file.isEmpty() || file.getSize() > 4 * 1024 * 1024) throw invalid("请选择不超过4MB的JPEG或PNG图片");
    byte[] bytes = file.getBytes();
    String extension;
    try (var input = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw invalid("文件内容不是可识别的JPEG或PNG图片");
      var reader = readers.next();
      try {
        reader.setInput(input);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!List.of("jpeg", "jpg", "png").contains(format)) throw invalid("仅支持JPEG和PNG图片");
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 1
            || height < 1
            || width > 6000
            || height > 6000
            || (long) width * height > 20000000) throw invalid("图片尺寸过大，请使用6000像素以内的图片");
        reader.read(0);
        extension = format.equals("png") ? "png" : "jpg";
      } finally {
        reader.dispose();
      }
    } catch (ResponseStatusException error) {
      throw error;
    } catch (Exception error) {
      throw invalid("图片已损坏，请重新选择");
    }
    String name = UUID.randomUUID() + "." + extension;
    Path target = directory.resolve(name).normalize();
    if (!target.getParent().equals(directory)) throw invalid("图片文件名无效");
    Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
    return Map.of("imageUrl", "/uploads/" + name);
  }

  private ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
