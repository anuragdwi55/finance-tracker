package com.example.fintrack.service;

import com.example.fintrack.model.Category;
import com.example.fintrack.model.Transaction;
import com.example.fintrack.model.User;
import com.example.fintrack.repository.TransactionRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ImportService {

    public record Mapping(
            String date, String amount, String description, String category,
            String debit, String credit, String dateFormat, Boolean amountIsCreditMinusDebit
    ) {}

    public static class Row {
        public LocalDate date;
        public BigDecimal amount;
        public String description;
        public String category; // optional incoming text
        public String error;
    }

    public record Preview(String uploadId, int totalRows, List<Row> sample, Mapping detected) {}

    public record CommitResult(int imported, int skippedDuplicates, int failed) {}

    private final TransactionRepository txRepo;

    public ImportService(TransactionRepository txRepo) { this.txRepo = txRepo; }

    // Simple in-memory staging (TTL implicit: we’ll just overwrite; fine for dev)
    private static final Map<String, List<Row>> STAGED = new ConcurrentHashMap<>();

    // Accept many date formats
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("MM/dd/uuuu"),
            DateTimeFormatter.ofPattern("dd-MMM-uuuu"),
            DateTimeFormatter.ofPattern("d/M/uuuu")
    );

    public Preview preview(MultipartFile file, Mapping mapping, User user) throws Exception {
        var parser = parse(file);
        var header = parser.getHeaderMap().keySet().stream().map(String::toLowerCase).toList();

        // Try detect if not provided
        Mapping m = mapping != null ? mapping : detect(header);

        List<Row> rows = new ArrayList<>();
        for (CSVRecord r : parser) {
            Row row = toRow(r, m);
            rows.add(row);
        }

        // Stage all parsed rows (even those with error; we’ll allow selection client-side)
        String uploadId = UUID.randomUUID().toString();
        STAGED.put(uploadId, rows);

        List<Row> sample = rows.subList(0, Math.min(rows.size(), 50));
        return new Preview(uploadId, rows.size(), sample, m);
    }

    public CommitResult commit(String uploadId, List<Integer> selectedIndexes, User user) {
        var staged = STAGED.get(uploadId);
        if (staged == null || staged.isEmpty()) return new CommitResult(0,0,0);

        int imported=0, dup=0, failed=0;
        for (int idx = 0; idx < staged.size(); idx++) {
            if (selectedIndexes != null && !selectedIndexes.contains(idx)) continue; // skip unselected
            Row row = staged.get(idx);
            if (row.error != null) { failed++; continue; }
            try {
                // dedupe: same user + date + amount + description (case-insensitive)
                boolean exists = txRepo.findByUserAndDateBetween(user, row.date, row.date)
                        .stream().anyMatch(t ->
                                t.getAmount().compareTo(row.amount)==0 &&
                                        eq(t.getNote(), row.description) &&
                                        eq(t.getCategory().name(), normalizeCat(row.category))
                        );
                if (exists) { dup++; continue; }

                Transaction t = new Transaction();
                t.setUser(user);
                t.setDate(row.date);
                t.setAmount(row.amount);
                t.setNote(row.description);
                Category cat = resolveCategory(row.category, row.amount);
                t.setCategory(cat);
                txRepo.save(t);
                imported++;
            } catch (Exception e) { failed++; }
        }
        // optional: clear staged
        STAGED.remove(uploadId);
        return new CommitResult(imported, dup, failed);
    }

    private static boolean eq(String a, String b){
        if (a==null && b==null) return true;
        if (a==null || b==null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private static Category resolveCategory(String catText, BigDecimal amount){
        if (catText == null || catText.isBlank()) {
            // Heuristic: amounts > 0 and cat missing -> assume INCOME if description hints
            return amount.signum() >= 0 ? Category.OTHER : Category.OTHER; // keep neutral; user can edit later
        }
        try { return Category.valueOf(catText.trim().toUpperCase()); }
        catch(Exception ignore){ return Category.OTHER; }
    }

    private static String get(org.apache.commons.csv.CSVRecord r, String col) {
        if (col == null || col.isBlank()) {
            throw new RuntimeException("Required column not specified");
        }
        // CSVRecord#toMap() gives header->value with original header casing
        Map<String,String> map = r.toMap();
        for (Map.Entry<String,String> e : map.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(col)) {
                String v = e.getValue();
                return v == null ? "" : v.trim();
            }
        }
        throw new RuntimeException("Missing column in CSV: " + col);
    }

    private static String getNullable(org.apache.commons.csv.CSVRecord r, String col) {
        if (col == null || col.isBlank()) return null;
        Map<String,String> map = r.toMap();
        for (Map.Entry<String,String> e : map.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(col)) {
                String v = e.getValue();
                v = (v == null) ? null : v.trim();
                return (v == null || v.isEmpty()) ? null : v;
            }
        }
        return null; // not present
    }

    private Row toRow(CSVRecord r, Mapping m){
        Row row = new Row();
        try {
            // Date
            String ds = get(r, m.date());
            row.date = parseDate(ds, m.dateFormat());

            // Amount (may be negative in CSV; normalize to positive for storage)
            BigDecimal amt;
            if (m.amount() != null && !m.amount().isBlank()) {
                amt = parseAmount(get(r, m.amount()));
            } else {
                BigDecimal debit  = parseAmountOrZero(get(r, m.debit()));
                BigDecimal credit = parseAmountOrZero(get(r, m.credit()));
                boolean creditMinusDebit = m.amountIsCreditMinusDebit()!=null && m.amountIsCreditMinusDebit();
                amt = creditMinusDebit ? credit.subtract(debit) : debit.subtract(credit);
            }

            boolean expenseSign = amt.signum() < 0;        // remember source sign
            BigDecimal normalized = amt.abs();             // ✅ store positive amounts
            row.amount = normalized;

            // Description & category
            row.description = get(r, m.description());
            String cat = getNullable(r, m.category());
            // If CSV didn’t supply a category, fallback using the sign: negative -> expense category OTHER, positive -> INCOME
            if (cat == null || cat.isBlank()) {
                row.category = expenseSign ? "OTHER" : "INCOME";
            } else {
                row.category = normalizeCat(cat);
            }

        } catch (Exception e) {
            row.error = e.getMessage();
        }
        return row;
    }


    private static String normalizeCat(String s){ return s==null?null:s.trim().toUpperCase().replace(' ', '_'); }

    private static BigDecimal parseAmount(String s){
        if (s==null) return BigDecimal.ZERO;
        String x = s.replace(",", "").trim();
        if (x.isEmpty()) return BigDecimal.ZERO;
        return new BigDecimal(x);
    }
    private static BigDecimal parseAmountOrZero(String s){
        try { return parseAmount(s); } catch(Exception e){ return BigDecimal.ZERO; }
    }

    private static LocalDate parseDate(String s, String preferred){
        if (s==null) throw new RuntimeException("Missing date");
        s = s.trim();
        if (preferred!=null && !preferred.isBlank()){
            return LocalDate.parse(s, DateTimeFormatter.ofPattern(preferred));
        }
        for (var f : DATE_FORMATS){
            try { return LocalDate.parse(s, f); } catch(Exception ignore){}
        }
        throw new RuntimeException("Unparseable date: " + s);
    }

    private static CSVParser parse(MultipartFile file) throws Exception {
        var br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
        return CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .build()
                .parse(br);
    }

    private static Mapping detect(Collection<String> headers){
        // very simple heuristics
        String date = first(headers, "date", "txn date", "posting date");
        String amount = first(headers, "amount", "amt");
        String desc = first(headers, "description", "narration", "details", "desc", "merchant");
        String category = first(headers, "category", "cat");
        String debit = first(headers, "debit", "withdrawal");
        String credit = first(headers, "credit", "deposit");
        Boolean creditMinusDebit = (amount==null) ? Boolean.TRUE : null; // when no single 'amount' column
        return new Mapping(date, amount, desc, category, debit, credit, null, creditMinusDebit);
    }

    private static String first(Collection<String> headers, String... options){
        for (String o : options){
            for (String h : headers){
                if (h.equalsIgnoreCase(o)) return h;
            }
        }
        return null;
    }
}
