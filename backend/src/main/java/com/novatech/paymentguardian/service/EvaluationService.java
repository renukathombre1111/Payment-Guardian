package com.novatech.paymentguardian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novatech.paymentguardian.dto.ApiDtos.EvaluationMetrics;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;

@Service
public class EvaluationService {

    private final ObjectMapper mapper;

    public EvaluationService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public EvaluationMetrics loadMetrics() {
        Path metricsFile = Path.of("..", "data", "out", "evaluation_metrics.json");
        Path alt = Path.of("data", "out", "evaluation_metrics.json");
        for (Path p : new Path[]{metricsFile, alt}) {
            if (p.toFile().exists()) {
                try {
                    return mapper.readValue(p.toFile(), EvaluationMetrics.class);
                } catch (Exception ignored) {
                }
            }
        }
        return new EvaluationMetrics(
                0, 0, 0, 0,
                0, 0, 0, 0, 0,
                Map.of(
                        "APPROVE", Map.of("APPROVE", 0, "REVIEW", 0, "BLOCK", 0),
                        "REVIEW", Map.of("APPROVE", 0, "REVIEW", 0, "BLOCK", 0),
                        "BLOCK", Map.of("APPROVE", 0, "REVIEW", 0, "BLOCK", 0)
                ),
                "Run `python data/evaluate.py` to generate synthetic evaluation metrics. No fabricated numbers served."
        );
    }
}
