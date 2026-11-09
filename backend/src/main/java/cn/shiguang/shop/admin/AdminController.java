// AI-assisted implementation with Codex. All routes require the ADMIN role in
// SecurityConfiguration.
package cn.shiguang.shop.admin;

import cn.shiguang.shop.order.OrderService;
import cn.shiguang.shop.order.OrderView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
  private final JdbcClient jdbc;
  private final OrderService orders;
  private final SalesReports reports;

  public AdminController(JdbcClient jdbc, OrderService orders, SalesReports reports) {
    this.jdbc = jdbc;
    this.orders = orders;
    this.reports = reports;
  }

  public record ProductEdit(
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Size(max = 40) String category,
      @NotNull @DecimalMin("0.01") @DecimalMax("999999.99") BigDecimal price,
      @Min(0) @Max(1000000) int stock,
      @NotBlank @Size(max = 255) String imageUrl,
      @Size(max = 100) @NotNull String tagline,
      @Size(max = 2000) @NotNull String description,
      boolean active) {}

  public record Shipping(@NotBlank @Size(max = 100) String trackingNumber) {}

  @GetMapping("/products")
  public Map<String, Object> products(
      @RequestParam(defaultValue = "") @Size(max = 100) String keyword,
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
    String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    var items =
        jdbc.sql(
                "SELECT"
                    + " id,name,category,price,stock,image_url,tagline,description,active,brand,source_type,source_url"
                    + " FROM product WHERE name LIKE :pattern ESCAPE '!' ORDER BY id DESC LIMIT"
                    + " :size OFFSET :offset")
            .param("pattern", pattern)
            .param("size", size)
            .param("offset", (long) (page - 1) * size)
            .query()
            .listOfRows();
    long total =
        jdbc.sql("SELECT COUNT(*) FROM product WHERE name LIKE :pattern ESCAPE '!'")
            .param("pattern", pattern)
            .query(Long.class)
            .single();
    return Map.of("items", items, "total", total, "page", page, "size", size);
  }

  @PostMapping("/products")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@Valid @RequestBody ProductEdit p) {
    checkImage(p.imageUrl());
    // The generated key avoids assuming any consecutive ID sequence.
    org.springframework.jdbc.support.GeneratedKeyHolder keys =
        new org.springframework.jdbc.support.GeneratedKeyHolder();
    jdbc.sql(
            "INSERT INTO product(name,category,price,stock,image_url,tagline,description,active)"
                + " VALUES (:name,:category,:price,:stock,:image,:tagline,:description,:active)")
        .param("name", p.name().trim())
        .param("category", p.category().trim())
        .param("price", p.price())
        .param("stock", p.stock())
        .param("image", p.imageUrl())
        .param("tagline", p.tagline())
        .param("description", p.description())
        .param("active", p.active())
        .update(keys);
    return Map.of("id", keys.getKey().longValue());
  }

  @PutMapping("/products/{id}")
  public Map<String, Boolean> update(@PathVariable long id, @Valid @RequestBody ProductEdit p) {
    checkImage(p.imageUrl());
    int changed =
        jdbc.sql(
                "UPDATE product SET"
                    + " name=:name,category=:category,price=:price,stock=:stock,image_url=:image,tagline=:tagline,description=:description,active=:active"
                    + " WHERE id=:id")
            .param("id", id)
            .param("name", p.name().trim())
            .param("category", p.category().trim())
            .param("price", p.price())
            .param("stock", p.stock())
            .param("image", p.imageUrl())
            .param("tagline", p.tagline())
            .param("description", p.description())
            .param("active", p.active())
            .update();
    if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
    return Map.of("saved", true);
  }

  @DeleteMapping("/products/{id}")
  public Map<String, Boolean> remove(@PathVariable long id) {
    int changed = jdbc.sql("UPDATE product SET active=FALSE WHERE id=:id").param("id", id).update();
    if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
    return Map.of("removed", true);
  }

  private void checkImage(String image) {
    if (!image.matches("/images/[A-Za-z0-9_./-]+")
        && !image.matches("/uploads/[a-f0-9-]{36}\\.(png|jpg)")
        && !image.matches("https://covers\\.openlibrary\\.org/b/id/[0-9]+-[SML]\\.jpg"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请上传图片，或选择本站已有商品图片");
    if (image.contains("..")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片路径无效");
  }

  @PostMapping("/products/{id}/restore")
  public Map<String, Boolean> restore(@PathVariable long id) {
    int changed = jdbc.sql("UPDATE product SET active=TRUE WHERE id=:id").param("id", id).update();
    if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
    return Map.of("restored", true);
  }

  @GetMapping("/orders")
  public OrderService.OrderPage orders(
      @RequestParam(defaultValue = "") @Size(max = 30) String status,
      @RequestParam(defaultValue = "") @Size(max = 80) String keyword,
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
    return orders.list(0, true, status, page, size, keyword);
  }

  @PostMapping("/orders/{id}/ship")
  public OrderView ship(@PathVariable long id, @Valid @RequestBody Shipping body) {
    return orders.ship(id, body.trackingNumber().trim());
  }

  @GetMapping("/stats")
  public Map<String, Object> stats(
      @RequestParam(required = false)
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(required = false)
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          LocalDate to,
      @RequestParam(defaultValue = "true") boolean includeSamples) {
    return reports.stats(from, to, includeSamples);
  }

  @GetMapping("/reports/sales.csv")
  public org.springframework.http.ResponseEntity<byte[]> salesCsv(
      @RequestParam(required = false)
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(required = false)
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          LocalDate to,
      @RequestParam(defaultValue = "true") boolean includeSamples) {
    return org.springframework.http.ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=shiguang-sales.csv")
        .contentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(reports.csv(from, to, includeSamples));
  }

  @GetMapping("/mail")
  public List<Map<String, Object>> mail() {
    return jdbc.sql(
            "SELECT"
                + " m.id,m.order_id,o.order_number,m.recipient,m.subject,m.body,m.status,m.attempts,m.created_at,m.sent_at,m.last_error"
                + " FROM mail_outbox m JOIN shop_order o ON o.id=m.order_id ORDER BY m.id DESC"
                + " LIMIT 100")
        .query()
        .listOfRows();
  }

  @PostMapping("/mail/{id}/retry")
  public Map<String, Boolean> retry(@PathVariable long id) {
    int changed =
        jdbc.sql(
                "UPDATE mail_outbox SET"
                    + " status='PENDING',attempts=0,last_error=NULL,next_attempt_at=CURRENT_TIMESTAMP"
                    + " WHERE id=:id AND status='FAILED'")
            .param("id", id)
            .update();
    if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "仅失败的邮件可以重试");
    return Map.of("queued", true);
  }
}
