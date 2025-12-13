package com.example.fintrack.service;

import com.example.fintrack.model.Alert;
import com.example.fintrack.model.Transaction;
import com.example.fintrack.model.Category; // <-- keep the enum your Transaction actually uses
import com.example.fintrack.repository.AlertRepository;
import com.example.fintrack.repository.TransactionRepository;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AlertService {
    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alerts;
    private final TransactionRepository txRepo;
    private final RestClient rest;
    private final String mlBase;

    public AlertService(AlertRepository alerts,
                        TransactionRepository txRepo,
                        RestClient rest,
                        @Value("${app.ml.base-url:http://localhost:8001}") String mlBase) {
        this.alerts = alerts; this.txRepo = txRepo; this.rest = rest; this.mlBase = mlBase;
    }

    @Transactional
    public void maybeCreateAnomaly(Transaction tx) {
        // only expenses
        if (tx.getCategory() == Category.INCOME) return;

        try {
            LocalDate start = tx.getDate().minusMonths(12);
            List<Double> hist = txRepo
                    .findByUserAndCategoryAndDateBetween(
                            tx.getUser(), tx.getCategory(), start, tx.getDate().minusDays(1))
                    .stream().map(t -> t.getAmount().doubleValue()).toList();

            double x = tx.getAmount().doubleValue();
            log.info("[ALERT] txId={} cat={} amount={} historyCount={}",
                    tx.getId(), tx.getCategory(), x, hist.size());

            boolean anomaly = false;

            // ---- ML check (best-effort) ----
            try {
                Map<String,Object> req = Map.of("history", hist, "candidate", x);
                Map<?,?> body = rest.post()
                        .uri(mlBase + "/anomaly/expense")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(req)
                        .retrieve()
                        .toEntity(Map.class)
                        .getBody();
                if (body != null) {
                    Object ia = body.get("is_anomaly");
                    Object sc = body.get("score");
                    Object mt = body.get("method");
                    log.info("[ALERT] ML response is_anomaly={} score={} method={}", ia, sc, mt);
                    anomaly = Boolean.TRUE.equals(ia);
                }
            } catch (Exception e) {
                log.warn("[ALERT] ML call failed: {}", e.toString());
            }

            // ---- deterministic fallback ----
            double median = 0.0;
            if (!hist.isEmpty()) {
                var s = new ArrayList<>(hist);
                s.sort(Double::compareTo);
                median = s.get(s.size()/2);
            }
            if (!anomaly) {
                if (hist.size() >= 3 && x > 1.8 * median) anomaly = true;
                else if (hist.size() < 3 && x >= 5000)   anomaly = true; // demo-friendly
            }

            if (anomaly) {
                Alert a = new Alert();
                a.setUser(tx.getUser());
                a.setType("ANOMALY");
                a.setSeverity((median > 0 && x > 2.5 * median) ? Alert.Severity.HIGH : Alert.Severity.MEDIUM);
                a.setTitle("Unusual " + tx.getCategory() + " spend");
                a.setMessage(String.format("₹%.0f on %s vs typical ₹%.0f", x, tx.getDate(), median));
                a.setTxId(tx.getId());
                alerts.save(a);
                log.info("[ALERT] created id={} for txId={}", a.getId(), tx.getId());
            } else {
                log.info("[ALERT] no alert (amount={} median={} size={})", x, median, hist.size());
            }
        } catch (Exception e) {
            log.error("[ALERT] maybeCreateAnomaly failed", e);
        }
    }
}
