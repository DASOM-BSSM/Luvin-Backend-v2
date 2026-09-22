package com.luvin.simulation.dto;

import java.util.List;

/**
 * GET /api/simulation/report 응답과 POST 요청에 공통으로 쓰는 구조.
 */
public class ReportData {
    private String summary;
    private List<String> strength;
    private List<String> weakness;
    private String advice;

    public ReportData() {}

    public ReportData(String summary, List<String> strength, List<String> weakness, String advice) {
        this.summary = summary;
        this.strength = strength;
        this.weakness = weakness;
        this.advice = advice;
    }

    public String getSummary() { return summary; }
    public List<String> getStrength() { return strength; }
    public List<String> getWeakness() { return weakness; }
    public String getAdvice() { return advice; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setStrength(List<String> strength) { this.strength = strength; }
    public void setWeakness(List<String> weakness) { this.weakness = weakness; }
    public void setAdvice(String advice) { this.advice = advice; }
}
