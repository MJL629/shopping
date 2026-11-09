// AI-assisted sales reporting. Revenue uses paid time; order counts use order creation time.
package cn.shiguang.shop.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalesReports {
  private final JdbcClient jdbc;

  public SalesReports(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  record Range(LocalDate from, LocalDate to) {}

  record Filter(String sql, Map<String, Object> values) {}

  private Range range(LocalDate from, LocalDate to) {
    if (from == null && to != null) from = to.minusDays(13);
    if (from != null && to == null) to = LocalDate.now();
    if (from != null && (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 365))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择前后顺序正确且不超过一年的日期范围");
    return new Range(from, to);
  }

  private Filter filter(String column, Range range, boolean includeSamples) {
    String sql = includeSamples ? "" : " AND o.sample=FALSE";
    Map<String, Object> values = new HashMap<>();
    if (range.from() != null) {
      sql += " AND " + column + ">=:from";
      values.put("from", range.from().atStartOfDay());
    }
    if (range.to() != null) {
      sql += " AND " + column + "<:until";
      values.put("until", range.to().plusDays(1).atStartOfDay());
    }
    return new Filter(sql, values);
  }

  public Map<String, Object> stats(LocalDate from, LocalDate to, boolean includeSamples) {
    Range selected = range(from, to);
    Filter created = filter("o.created_at", selected, includeSamples),
        paid = filter("o.paid_at", selected, includeSamples);
    var counts =
        jdbc.sql(
                "SELECT COUNT(*) AS totalOrders,COALESCE(SUM(CASE WHEN"
                    + " o.status='PENDING_PAYMENT' THEN 1 ELSE 0 END),0) AS"
                    + " pendingPayment,COALESCE(SUM(CASE WHEN o.status='PAID' THEN 1 ELSE 0"
                    + " END),0) AS pendingShipment FROM shop_order o WHERE 1=1"
                    + created.sql())
            .params(created.values())
            .query()
            .singleRow();
    Map<String, Object> summary = new LinkedHashMap<>();
    for (String key : List.of("totalOrders", "pendingPayment", "pendingShipment"))
      summary.put(key, counts.get(key));
    BigDecimal revenue =
        jdbc.sql(
                "SELECT COALESCE(SUM(o.total_amount),0) FROM shop_order o WHERE o.status IN"
                    + " ('PAID','SHIPPED','COMPLETED')"
                    + paid.sql())
            .params(paid.values())
            .query(BigDecimal.class)
            .single();
    summary.put("revenue", revenue);
    Range trendRange =
        selected.from() == null
            ? new Range(LocalDate.now().minusDays(13), LocalDate.now())
            : selected;
    Filter trendFilter = filter("o.paid_at", trendRange, includeSamples);
    var trend =
        jdbc.sql(
                "SELECT CAST(o.paid_at AS DATE) AS `day`,SUM(o.total_amount) AS revenue,COUNT(*) AS"
                    + " orders FROM shop_order o WHERE o.status IN ('PAID','SHIPPED','COMPLETED')"
                    + trendFilter.sql()
                    + " GROUP BY CAST(o.paid_at AS DATE) ORDER BY `day`")
            .params(trendFilter.values())
            .query()
            .listOfRows();
    var top =
        jdbc.sql(
                "SELECT i.product_id,i.product_name,SUM(i.quantity) AS"
                    + " units,SUM(i.quantity*i.unit_price) AS revenue FROM order_item i JOIN"
                    + " shop_order o ON o.id=i.order_id WHERE o.status IN"
                    + " ('PAID','SHIPPED','COMPLETED')"
                    + paid.sql()
                    + " GROUP BY i.product_id,i.product_name ORDER BY revenue DESC LIMIT 6")
            .params(paid.values())
            .query()
            .listOfRows();
    var distribution =
        jdbc.sql(
                "SELECT o.status,COUNT(*) AS count FROM shop_order o WHERE 1=1"
                    + created.sql()
                    + " GROUP BY o.status")
            .params(created.values())
            .query()
            .listOfRows();
    long
        products =
            jdbc.sql("SELECT COUNT(*) FROM product WHERE active=TRUE").query(Long.class).single(),
        customers =
            jdbc.sql("SELECT COUNT(*) FROM app_user WHERE role='CUSTOMER'")
                .query(Long.class)
                .single();
    return Map.of(
        "summary",
        summary,
        "activeProducts",
        products,
        "customers",
        customers,
        "trend",
        normalize(trend),
        "topProducts",
        normalize(top),
        "statusDistribution",
        normalize(distribution),
        "range",
        Map.of(
            "from",
            trendRange.from().toString(),
            "to",
            trendRange.to().toString(),
            "includeSamples",
            includeSamples));
  }

  private List<Map<String, Object>> normalize(List<Map<String, Object>> rows) {
    return rows.stream()
        .map(
            row -> {
              Map<String, Object> result = new LinkedHashMap<>();
              row.forEach((key, value) -> result.put(key.toLowerCase(Locale.ROOT), value));
              return result;
            })
        .toList();
  }

  public byte[] csv(LocalDate from, LocalDate to, boolean includeSamples) {
    var report = stats(from, to, includeSamples);
    @SuppressWarnings("unchecked")
    var rows = (List<Map<String, Object>>) report.get("trend");
    StringBuilder csv = new StringBuilder("\uFEFF日期,已付订单数,销售额（人民币演示值）\r\n");
    for (var row : rows)
      csv.append(row.get("day"))
          .append(',')
          .append(row.get("orders"))
          .append(',')
          .append(row.get("revenue"))
          .append("\r\n");
    return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }
}
