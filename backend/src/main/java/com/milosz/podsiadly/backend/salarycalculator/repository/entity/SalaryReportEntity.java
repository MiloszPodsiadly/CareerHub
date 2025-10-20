package com.milosz.podsiadly.backend.salarycalculator.repository.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RedisHash("salary_report")
@TypeAlias("SalaryReportEntity")
public class SalaryReportEntity {

    @Id
    private String id = UUID.randomUUID().toString();

    private Instant createdAt = Instant.now();

    private BigDecimal gross;
    private BigDecimal net;
    private BigDecimal yearlyNet;

    private Items items;
    private Map<String, BigDecimal> details;
    private Map<String, BigDecimal> proportions;

    private List<Monthly> monthly;

    private RequestSnapshot request;

    @TimeToLive
    private Long ttl;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public BigDecimal getGross() { return gross; }
    public void setGross(BigDecimal gross) { this.gross = gross; }
    public BigDecimal getNet() { return net; }
    public void setNet(BigDecimal net) { this.net = net; }
    public BigDecimal getYearlyNet() { return yearlyNet; }
    public void setYearlyNet(BigDecimal yearlyNet) { this.yearlyNet = yearlyNet; }
    public Items getItems() { return items; }
    public void setItems(Items items) { this.items = items; }
    public Map<String, BigDecimal> getDetails() { return details; }
    public void setDetails(Map<String, BigDecimal> details) { this.details = details; }
    public Map<String, BigDecimal> getProportions() { return proportions; }
    public void setProportions(Map<String, BigDecimal> proportions) { this.proportions = proportions; }
    public List<Monthly> getMonthly() { return monthly; }
    public void setMonthly(List<Monthly> monthly) { this.monthly = monthly; }
    public RequestSnapshot getRequest() { return request; }
    public void setRequest(RequestSnapshot request) { this.request = request; }
    public Long getTtl() { return ttl; }
    public void setTtl(Long ttl) { this.ttl = ttl; }

    public static class Items {
        private BigDecimal pension;
        private BigDecimal disability;
        private BigDecimal sickness;
        private BigDecimal health;
        private BigDecimal pit;
        public Items() {}
        public Items(BigDecimal pension, BigDecimal disability, BigDecimal sickness, BigDecimal health, BigDecimal pit) {
            this.pension = pension; this.disability = disability; this.sickness = sickness; this.health = health; this.pit = pit;
        }
        public BigDecimal getPension(){ return pension; }
        public BigDecimal getDisability(){ return disability; }
        public BigDecimal getSickness(){ return sickness; }
        public BigDecimal getHealth(){ return health; }
        public BigDecimal getPit(){ return pit; }
        public void setPension(BigDecimal v){ this.pension=v; }
        public void setDisability(BigDecimal v){ this.disability=v; }
        public void setSickness(BigDecimal v){ this.sickness=v; }
        public void setHealth(BigDecimal v){ this.health=v; }
        public void setPit(BigDecimal v){ this.pit=v; }
    }

    public static class Monthly {
        private String month;
        private BigDecimal gross;
        private BigDecimal social;
        private BigDecimal health;
        private BigDecimal pit;
        private BigDecimal net;
        public Monthly() {}
        public Monthly(String month, BigDecimal gross, BigDecimal social, BigDecimal health, BigDecimal pit, BigDecimal net) {
            this.month=month; this.gross=gross; this.social=social; this.health=health; this.pit=pit; this.net=net;
        }
        public String getMonth(){ return month; }
        public BigDecimal getGross(){ return gross; }
        public BigDecimal getSocial(){ return social; }
        public BigDecimal getHealth(){ return health; }
        public BigDecimal getPit(){ return pit; }
        public BigDecimal getNet(){ return net; }
        public void setMonth(String v){ this.month=v; }
        public void setGross(BigDecimal v){ this.gross=v; }
        public void setSocial(BigDecimal v){ this.social=v; }
        public void setHealth(BigDecimal v){ this.health=v; }
        public void setPit(BigDecimal v){ this.pit=v; }
        public void setNet(BigDecimal v){ this.net=v; }
    }

    public static class RequestSnapshot {
        private String contractType;
        private String amountMode;
        private Integer year;
        private java.util.Map<String, Object> options;
        public RequestSnapshot() {}
        public RequestSnapshot(String contractType, String amountMode, Integer year, java.util.Map<String, Object> options) {
            this.contractType = contractType; this.amountMode = amountMode; this.year=year; this.options=options;
        }
        public String getContractType(){ return contractType; }
        public String getAmountMode(){ return amountMode; }
        public Integer getYear(){ return year; }
        public java.util.Map<String,Object> getOptions(){ return options; }
        public void setContractType(String v){ this.contractType=v; }
        public void setAmountMode(String v){ this.amountMode=v; }
        public void setYear(Integer v){ this.year=v; }
        public void setOptions(java.util.Map<String,Object> v){ this.options=v; }
    }
}
