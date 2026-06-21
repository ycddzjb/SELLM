package com.sellm.research;

public class ReliabilityCalc {
    private Long id;
    private Long ownerId;
    private String dataset;   // 输入矩阵 JSON
    private String method;
    private String result;    // 结果 JSON

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getDataset() { return dataset; }
    public void setDataset(String dataset) { this.dataset = dataset; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
}
