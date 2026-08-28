package com.mg.mgserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "algo_config")
public class AlgoConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "algo_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "task_id")
    private DispatchTask task;

    @Column(name = "bee_count", nullable = false)
    private int beeCount = 60;

    @Column(name = "max_iter", nullable = false)
    private int maxIter = 300;

    @Column(name = "algorithm_limit", nullable = false)
    private int limit = 120;

    @Column(name = "archive_size", nullable = false)
    private int archiveSize = 80;

    @Column(name = "tournament_size", nullable = false)
    private int tournamentSize = 3;

    @Column(name = "elite_rate", nullable = false)
    private double eliteRate = 0.25;

    @Column(name = "elimination_rate", nullable = false)
    private double eliminationRate = 0.25;

    @Column(name = "archive_guidance_rate", nullable = false)
    private double archiveGuidanceRate = 0.40;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public DispatchTask getTask() {
        return task;
    }

    public void setTask(DispatchTask task) {
        this.task = task;
    }

    public int getBeeCount() {
        return beeCount;
    }

    public void setBeeCount(int beeCount) {
        this.beeCount = beeCount;
    }

    public int getMaxIter() {
        return maxIter;
    }

    public void setMaxIter(int maxIter) {
        this.maxIter = maxIter;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getArchiveSize() {
        return archiveSize;
    }

    public void setArchiveSize(int archiveSize) {
        this.archiveSize = archiveSize;
    }

    public int getTournamentSize() {
        return tournamentSize;
    }

    public void setTournamentSize(int tournamentSize) {
        this.tournamentSize = tournamentSize;
    }

    public double getEliteRate() {
        return eliteRate;
    }

    public void setEliteRate(double eliteRate) {
        this.eliteRate = eliteRate;
    }

    public double getEliminationRate() {
        return eliminationRate;
    }

    public void setEliminationRate(double eliminationRate) {
        this.eliminationRate = eliminationRate;
    }

    public double getArchiveGuidanceRate() {
        return archiveGuidanceRate;
    }

    public void setArchiveGuidanceRate(double archiveGuidanceRate) {
        this.archiveGuidanceRate = archiveGuidanceRate;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
