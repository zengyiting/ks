package com.example.recommend.web;

import com.example.recommend.service.OfflineEvaluationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

@RestController
@RequestMapping("/api/evaluations")
public class EvaluationController {
    private final OfflineEvaluationService offlineEvaluationService;

    public EvaluationController(OfflineEvaluationService offlineEvaluationService) {
        this.offlineEvaluationService = offlineEvaluationService;
    }

    @GetMapping("/offline")
    public OfflineEvaluationService.EvaluationReport offline(
            @RequestParam(name = "k", defaultValue = "10") int k,
            @RequestParam(name = "testRatio", defaultValue = "0.2") double testRatio,
            @RequestParam(name = "relevance", defaultValue = "1.5") double relevance
    ) {
        return offlineEvaluationService.evaluate(k, testRatio, relevance);
    }

    @GetMapping(value = "/offline/csv", produces = "text/csv")
    public ResponseEntity<String> offlineCsv(
                @RequestParam(name = "k", defaultValue = "10") int k,
                @RequestParam(name = "testRatio", defaultValue = "0.2") double testRatio,
                @RequestParam(name = "relevance", defaultValue = "1.5") double relevance
    ) {
        OfflineEvaluationService.EvaluationReport report = offlineEvaluationService.evaluate(k, testRatio, relevance);
        StringJoiner csv = new StringJoiner("\n");
        csv.add("algorithm,topK,testRatio,relevance,precisionAtK,recallAtK,ndcgAtK,coverage,users,trainSize,testSize,evaluableUsers");
        for (Map.Entry<String, OfflineEvaluationService.AlgorithmMetrics> e : report.metrics().entrySet()) {
            OfflineEvaluationService.AlgorithmMetrics m = e.getValue();
            csv.add(String.join(",",
                    e.getKey(),
                    String.valueOf(report.topK()),
                    String.valueOf(report.testRatio()),
                    String.valueOf(report.relevanceThreshold()),
                    String.valueOf(m.precisionAtK()),
                    String.valueOf(m.recallAtK()),
                    String.valueOf(m.ndcgAtK()),
                    String.valueOf(m.coverage()),
                    String.valueOf(m.users()),
                    String.valueOf(report.trainSize()),
                    String.valueOf(report.testSize()),
                    String.valueOf(report.evaluableUsers())
            ));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=offline-evaluation.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString());
    }
}

