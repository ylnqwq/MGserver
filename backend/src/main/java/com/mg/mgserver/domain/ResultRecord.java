package com.mg.mgserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "result_record")
public class ResultRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private DispatchTask task;

    @Column(name = "economic_cost", nullable = false)
    private double economicCost;

    @Column(name = "environmental_cost", nullable = false)
    private double environmentCost;

    @Column(name = "renewable_rate", nullable = false)
    private double renewableUtilizationRate;

    @Column(name = "final_soc", nullable = false)
    private double finalSoc;

    @Column(name = "current_scheme", length = 64)
    private String currentScheme;

    @Column(name = "pareto_path", length = 500)
    private String paretoPath;

    @Column(name = "result_path", length = 500)
    private String resultPath;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public DispatchTask getTask() {
        return task;
    }

    public void setTask(DispatchTask task) {
        this.task = task;
    }

    public double getEconomicCost() {
        return economicCost;
    }

    public void setEconomicCost(double economicCost) {
        this.economicCost = economicCost;
    }

    public double getEnvironmentCost() {
        return environmentCost;
    }

    public void setEnvironmentCost(double environmentCost) {
        this.environmentCost = environmentCost;
    }

    public double getRenewableUtilizationRate() {
        return renewableUtilizationRate;
    }

    public void setRenewableUtilizationRate(double renewableUtilizationRate) {
        this.renewableUtilizationRate = renewableUtilizationRate;
    }

    public double getFinalSoc() {
        return finalSoc;
    }

    public void setFinalSoc(double finalSoc) {
        this.finalSoc = finalSoc;
    }

    public String getCurrentScheme() {
        return currentScheme;
    }

    public void setCurrentScheme(String currentScheme) {
        this.currentScheme = currentScheme;
    }

    public String getParetoPath() {
        return paretoPath;
    }

    public void setParetoPath(String paretoPath) {
        this.paretoPath = paretoPath;
    }

    public String getResultPath() {
        return resultPath;
    }

    public void setResultPath(String resultPath) {
        this.resultPath = resultPath;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
