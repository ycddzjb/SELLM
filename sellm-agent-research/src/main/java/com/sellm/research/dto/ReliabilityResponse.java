package com.sellm.research.dto;

import com.sellm.research.ReliabilityResult;

public class ReliabilityResponse {
    private Long id;
    private ReliabilityResult result;
    public ReliabilityResponse(Long id, ReliabilityResult result) { this.id = id; this.result = result; }
    public Long getId() { return id; }
    public ReliabilityResult getResult() { return result; }
}
