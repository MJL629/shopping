// AI-assisted regression tests for merchant JSON, upload safety, report scopes and admin
// operations.
package cn.shiguang.shop;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:admintools;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "SHOP_UPLOAD_DIR=.local/test-uploads",
      "DEMO_SEED_ORDERS=false",
      "MAIL_POLL_MS=3600000"
    })
@AutoConfigureMockMvc
@Transactional
class AdminToolsTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper json;

  long createUser() {
    String name = "report_" + UUID.randomUUID().toString().substring(0, 12);
    jdbc.sql(
            "INSERT INTO app_user(username,display_name,email,password_hash) VALUES"
                + " (:name,'Report',:email,'test-only')")
        .param("name", name)
        .param("email", name + "@example.test")
        .update();
    return jdbc.sql("SELECT id FROM app_user WHERE username=:name")
        .param("name", name)
        .query(Long.class)
        .single();
  }

  void order(long user, String number, int amount, boolean sample, String created) {
    jdbc.sql(
            "INSERT INTO"
                + " shop_order(order_number,user_id,request_key,status,total_amount,recipient_name,recipient_email,recipient_phone,shipping_address,created_at,paid_at,sample)"
                + " VALUES"
                + " (:number,:user,:key,'PAID',:amount,'Test','test@example.test','13800000000','Fictional"
                + " campus',:created,:paid,:sample)")
        .param("number", number)
        .param("user", user)
        .param("key", UUID.randomUUID().toString())
        .param("amount", amount)
        .param("created", java.time.LocalDateTime.parse(created + "T12:00:00"))
        .param("paid", java.time.LocalDateTime.parse("2026-11-08T13:00:00"))
        .param("sample", sample)
        .update();
  }

  @Test
  void reportsSeparatePaymentDateFromCreationDateAndCanExcludeSamples() throws Exception {
    long u = createUser();
    order(u, "TEST-SAMPLE", 100, true, "2026-11-08");
    order(u, "TEST-OLDER", 50, false, "2026-11-07");
    order(u, "TEST-NEWER", 20, false, "2026-11-08");
    mvc.perform(
            get("/api/admin/stats")
                .with(user("admin").roles("ADMIN"))
                .param("from", "2026-11-08")
                .param("to", "2026-11-08")
                .param("includeSamples", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.revenue").value(70))
        .andExpect(jsonPath("$.summary.totalOrders").value(1));
    mvc.perform(
            get("/api/admin/stats")
                .with(user("admin").roles("ADMIN"))
                .param("from", "2026-11-08")
                .param("to", "2026-11-08"))
        .andExpect(jsonPath("$.summary.revenue").value(170));
    mvc.perform(
            get("/api/admin/reports/sales.csv")
                .with(user("admin").roles("ADMIN"))
                .param("from", "2026-11-08")
                .param("to", "2026-11-08")
                .param("includeSamples", "false"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("2026-11-08,2,70.00")));
  }

  @Test
  void invalidReportRangeAndUnauthorizedExportAreRejected() throws Exception {
    mvc.perform(
            get("/api/admin/stats")
                .with(user("admin").roles("ADMIN"))
                .param("from", "2026-11-09")
                .param("to", "2026-11-01"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/admin/reports/sales.csv")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/admin/reports/sales.csv").with(user("customer").roles("CUSTOMER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void safeImageUploadUsesGeneratedFilenameAndIsReadable() throws Exception {
    var output = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
    var file = new MockMultipartFile("file", "../../cover.png", "image/png", output.toByteArray());
    var result =
        mvc.perform(
                multipart("/api/admin/images")
                    .file(file)
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf()))
            .andExpect(status().isCreated())
            .andReturn();
    String url = json.readTree(result.getResponse().getContentAsString()).get("imageUrl").asText();
    assertThat(url).matches("/uploads/[a-f0-9-]{36}\\.png");
    mvc.perform(get(url))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("image/png"));
  }

  @Test
  void executableOrOversizedUploadsAndCustomerUploadsAreRejected() throws Exception {
    var svg =
        new MockMultipartFile(
            "file", "cover.svg", "image/svg+xml", "<svg onload='alert(1)'/>".getBytes());
    mvc.perform(
            multipart("/api/admin/images")
                .file(svg)
                .with(user("admin").roles("ADMIN"))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart("/api/admin/images")
                .file(svg)
                .with(user("customer").roles("CUSTOMER"))
                .with(csrf()))
        .andExpect(status().isForbidden());
    var large =
        new MockMultipartFile("file", "large.png", "image/png", new byte[4 * 1024 * 1024 + 1]);
    mvc.perform(
            multipart("/api/admin/images")
                .file(large)
                .with(user("admin").roles("ADMIN"))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void merchantMetadataIsValidJsonAndHasTraceableOriginalPricing() throws Exception {
    var rows =
        jdbc.sql(
                "SELECT gallery_json,specifications_json,source_price,source_url FROM product WHERE"
                    + " source_type='MERCHANT_CATALOG'")
            .query()
            .listOfRows();
    assertThat(rows).hasSize(100);
    for (var row : rows) {
      assertThat(json.readTree(row.get("gallery_json").toString()).size())
          .isGreaterThanOrEqualTo(2);
      assertThat(json.readTree(row.get("specifications_json").toString()).size())
          .isGreaterThanOrEqualTo(4);
      assertThat(row.get("source_url").toString()).startsWith("https://www.muji.us/products/");
      assertThat(new java.math.BigDecimal(row.get("source_price").toString())).isPositive();
    }
  }

  @Test
  void adminOrderSearchEscapesWildcardsAndProductCanBeRestored() throws Exception {
    long u = createUser();
    String number = "TEST-SEARCH-" + UUID.randomUUID().toString().substring(0, 12);
    order(u, number, 20, false, "2026-11-08");
    mvc.perform(
            get("/api/admin/orders").with(user("admin").roles("ADMIN")).param("keyword", number))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));
    mvc.perform(get("/api/admin/orders").with(user("admin").roles("ADMIN")).param("keyword", "%"))
        .andExpect(jsonPath("$.total").value(0));
    jdbc.sql("UPDATE product SET active=FALSE WHERE id=1").update();
    mvc.perform(
            post("/api/admin/products/1/restore").with(user("admin").roles("ADMIN")).with(csrf()))
        .andExpect(status().isOk());
    assertThat(jdbc.sql("SELECT active FROM product WHERE id=1").query(Boolean.class).single())
        .isTrue();
  }
}
