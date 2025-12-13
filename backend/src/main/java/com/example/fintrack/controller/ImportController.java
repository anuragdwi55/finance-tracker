package com.example.fintrack.controller;

import com.example.fintrack.model.User;
import com.example.fintrack.repository.UserRepository;
import com.example.fintrack.service.ImportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/import")
public class ImportController {

    private final ImportService svc;
    private final UserRepository users;

    public ImportController(ImportService svc, UserRepository users) {
        this.svc = svc; this.users = users;
    }

    @PostMapping(value="/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportService.Preview preview(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value="mapping", required = false) ImportService.Mapping mapping,
            Authentication auth) throws Exception {
        User u = users.findByEmail(auth.getName()).orElseThrow();
        return svc.preview(file, mapping, u);
    }

    record CommitReq(String uploadId, List<Integer> selected) {}

    @PostMapping("/commit")
    public Map<String, Object> commit(@RequestBody CommitReq req, Authentication auth) {
        User u = users.findByEmail(auth.getName()).orElseThrow();
        var res = svc.commit(req.uploadId(), req.selected(), u);
        return Map.of("imported", res.imported(), "skippedDuplicates", res.skippedDuplicates(), "failed", res.failed());
    }
}
