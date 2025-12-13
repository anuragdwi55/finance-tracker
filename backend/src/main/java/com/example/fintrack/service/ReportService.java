
package com.example.fintrack.service;

import com.example.fintrack.dto.ForecastRequest;
import com.example.fintrack.dto.SavingsMonth;
import com.example.fintrack.model.Budget;
import com.example.fintrack.model.Transaction;
import com.example.fintrack.model.Category;
import com.example.fintrack.model.User;
import com.example.fintrack.repository.BudgetRepository;
import com.example.fintrack.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final TransactionRepository txRepo;
    private final BudgetRepository budgetRepo;
    private final RestClient mlClient;

    public ReportService(TransactionRepository txRepo, BudgetRepository budgetRepo, RestClient mlClient) {
        this.txRepo = txRepo;
        this.budgetRepo = budgetRepo;
        this.mlClient = mlClient;
    }

    private static String tdRight(String s) {
        return "<td style='text-align:right'>" + s + "</td>";
    }

    public String renderMonthlyHtml(User user, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);

        List<Transaction> txs = txRepo.findByUserAndDateBetween(user, start, end);
        BigDecimal income = sum(txs.stream().filter(t -> t.getCategory() == Category.INCOME));
        BigDecimal expense = sum(txs.stream().filter(t -> t.getCategory() != Category.INCOME));
        BigDecimal savings = income.subtract(expense);

        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (Category c : Category.values()) {
            if (c == Category.INCOME) continue;
            BigDecimal sum = sum(txs.stream().filter(t -> t.getCategory() == c));
            byCategory.put(c.name(), sum);
        }

        List<Budget> budgets = budgetRepo.findByUserAndYearAndMonth(user, year, month);
        Map<String, BigDecimal> limits = budgets.stream().collect(Collectors.toMap(
                b -> b.getCategory().name(), Budget::getLimitAmount, (a,b)->b, LinkedHashMap::new));

        List<Map<String,Object>> rows = new ArrayList<>();
        for (var e : byCategory.entrySet()) {
            BigDecimal spent = e.getValue();
            BigDecimal limit = limits.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal remaining = limit.subtract(spent);
            double pct = limit.compareTo(BigDecimal.ZERO) == 0 ? 0.0 :
                    spent.divide(limit, 4, RoundingMode.HALF_UP).doubleValue();
            rows.add(Map.of(
                    "category", e.getKey(),
                    "limit", limit,
                    "spent", spent,
                    "remaining", remaining,
                    "pct", pct
            ));
        }

        double forecastNext = forecastNextSavings(user);

        String monthLabel = Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset='utf-8'><style>")
            .append("body{font-family:Inter,system-ui,-apple-system,Segoe UI,Roboto,Arial,sans-serif;color:#222;margin:0;padding:24px;background:#f6f7f8}")
            .append(".card{background:#fff;border-radius:14px;box-shadow:0 1px 8px rgba(0,0,0,.06);padding:16px}")
            .append(".grid{display:grid;gap:12px;grid-template-columns:repeat(4,1fr)}")
            .append("table{width:100%;border-collapse:collapse;font-size:14px}")
            .append("th,td{padding:10px;border-top:1px solid #eee;text-align:left}")
            .append("th{background:#fafafa;text-transform:uppercase;font-size:12px;letter-spacing:.03em;color:#666}")
            .append(".bar{height:8px;background:#eee;border-radius:6px;overflow:hidden}")
            .append(".fill{height:8px;background:#111;border-radius:6px}")
            .append("</style></head><body>");

        html.append("<h2 style='margin:0 0 12px 0'>Monthly Finance Report — ").append(monthLabel).append("</h2>");

        html.append("<div class='grid' style='margin:12px 0 16px 0'>");
        html.append(stat("Income", income));
        html.append(stat("Expenses", expense));
        html.append(stat("Savings", savings));
        html.append(stat("Forecast (Next Month)", BigDecimal.valueOf(forecastNext)));
        html.append("</div>");

        html.append("<div class='card'>");
        html.append("<h3 style='margin:4px 0 8px 0'>Budgets vs Spend</h3>");
        html.append("<table><thead><tr><th>Category</th><th style='text-align:right'>Limit</th><th style='text-align:right'>Spent</th><th style='text-align:right'>Remaining</th><th>Progress</th></tr></thead><tbody>");
        for (var row : rows) {
            String cat = (String)row.get("category");
            BigDecimal limit = (BigDecimal)row.get("limit");
            BigDecimal spent = (BigDecimal)row.get("spent");
            BigDecimal remaining = (BigDecimal)row.get("remaining");
            double pct = (double)row.get("pct");
            int pct100 = (int)Math.round(Math.max(0, Math.min(100, pct*100)));
            html.append("<tr>")
                .append("<td>").append(cat).append("</td>")
                .append(tdRight("₹ " + fmt(limit)))
                .append(tdRight("₹ " + fmt(spent)))
                .append(tdRight("₹ " + fmt(remaining)))
                .append("<td><div class='bar'><div class='fill' style='width:").append(pct100).append("%'></div></div></td>")
                .append("</tr>");
        }
        html.append("</tbody></table></div>");

        html.append("<p style='color:#666;margin-top:10px'>Tip: set monthly budgets in the app to track progress. This email was generated automatically.</p>");
        html.append("</body></html>");
        return html.toString();
    }

    private String stat(String label, BigDecimal value) {
        return "<div class='card'><div style='font-size:12px;color:#666'>" + label + "</div>"
                + "<div style='font-size:20px;font-weight:600'>₹ " + fmt(value) + "</div></div>";
    }

    private static BigDecimal sum(java.util.stream.Stream<Transaction> s) {
        return s.map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String fmt(BigDecimal v) {
        if (v == null) return "0";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public double forecastNextSavings(User user) {
        LocalDate now = LocalDate.now().withDayOfMonth(1);
        List<SavingsMonth> months = new ArrayList<>();
        for (int i = 6; i >= 1; i--) {
            LocalDate start = now.minusMonths(i);
            LocalDate end = start.plusMonths(1).minusDays(1);
            List<Transaction> txs = txRepo.findByUserAndDateBetween(user, start, end);
            BigDecimal incomeBD = sum(txs.stream().filter(t -> t.getCategory() == Category.INCOME));
            BigDecimal expenseBD = sum(txs.stream().filter(t -> t.getCategory() != Category.INCOME));
            double income = incomeBD.doubleValue();
            double expense = expenseBD.doubleValue();
            if (income > 0.0 || expense > 0.0) {
                months.add(new SavingsMonth(start.getYear(), start.getMonthValue(), income, expense));
            }
        }
        if (months.isEmpty()) return 0.0;

        ForecastRequest payload = new ForecastRequest(months);
        try {
            Map resp = mlClient.post().uri("/predict/savings").body(payload).retrieve().toEntity(Map.class).getBody();
            Object v = resp.get("next_month_savings");
            return v instanceof Number ? ((Number)v).doubleValue() : 0.0;
        } catch (Exception ex) {
            double avg = months.stream().mapToDouble(m -> m.income() - m.expense()).average().orElse(0.0);
            return Math.round(avg * 100.0) / 100.0;
        }
    }
}
