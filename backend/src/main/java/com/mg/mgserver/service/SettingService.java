package com.mg.mgserver.service;

import com.mg.mgserver.domain.AlgoConfig;
import com.mg.mgserver.domain.DeviceParam;
import com.mg.mgserver.domain.DispatchTask;
import com.mg.mgserver.dto.SettingDtos.AlgorithmSettingRequest;
import com.mg.mgserver.dto.SettingDtos.AlgorithmSettingResponse;
import com.mg.mgserver.dto.SettingDtos.SettingRequest;
import com.mg.mgserver.dto.SettingDtos.SettingResponse;
import com.mg.mgserver.repository.AlgoConfigRepository;
import com.mg.mgserver.repository.DeviceParamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingService {
    private static final long DEFAULT_ROW_ID = 0L;
    private static final int DEFAULT_BEE = 60;
    private static final int DEFAULT_MAX_ITER = 300;
    private static final int DEFAULT_LIMIT = 120;
    private static final int DEFAULT_ARCHIVE_SIZE = 80;
    private static final int DEFAULT_TOURNAMENT_SIZE = 3;
    private static final double DEFAULT_ELITE_RATE = 0.25;
    private static final double DEFAULT_ELIMINATION_RATE = 0.25;
    private static final double DEFAULT_ARCHIVE_GUIDANCE_RATE = 0.40;

    private final DeviceParamRepository deviceParamRepository;
    private final AlgoConfigRepository algoConfigRepository;

    public SettingService(DeviceParamRepository deviceParamRepository, AlgoConfigRepository algoConfigRepository) {
        this.deviceParamRepository = deviceParamRepository;
        this.algoConfigRepository = algoConfigRepository;
    }

    @Transactional
    public DeviceParam getOrCreate() {
        return deviceParamRepository.findById(DEFAULT_ROW_ID)
                .orElseGet(() -> deviceParamRepository.save(new DeviceParam()));
    }

    @Transactional
    public SettingResponse update(SettingRequest request) {
        validateRange(request);
        DeviceParam snapshot = getOrCreate();
        snapshot.setTask(null);
        snapshot.setMicroTurbineMinKw(request.microTurbineMinKw());
        snapshot.setMicroTurbineMaxKw(request.microTurbineMaxKw());
        snapshot.setMicroTurbineRampUpKw(request.microTurbineRampUpKw());
        snapshot.setMicroTurbineRampDownKw(request.microTurbineRampDownKw());
        snapshot.setMicroTurbineUnitCost(request.microTurbineUnitCost());
        snapshot.setBatteryCapacityKwh(request.batteryCapacityKwh());
        snapshot.setBatteryChargeMaxKw(request.batteryChargeMaxKw());
        snapshot.setBatteryDischargeMaxKw(request.batteryDischargeMaxKw());
        snapshot.setBatterySocMin(request.batterySocMin());
        snapshot.setBatterySocMax(request.batterySocMax());
        snapshot.setBatterySocInitial(request.batterySocInitial());
        snapshot.setGridBuyMaxKw(request.gridBuyMaxKw());
        snapshot.setGridSellMaxKw(request.gridSellMaxKw());
        snapshot.setRenewableCurtailmentCost(request.renewableCurtailmentCost());
        snapshot.touch();
        return SettingResponse.from(deviceParamRepository.save(snapshot));
    }

    @Transactional
    public AlgorithmSettingResponse algorithm() {
        return AlgorithmSettingResponse.from(getOrCreateAlgorithm());
    }

    @Transactional
    public AlgorithmSettingResponse algorithmForTask(Long taskId) {
        return AlgorithmSettingResponse.from(algoConfigRepository.findFirstByTask_IdOrderByUpdatedAtDesc(taskId)
                .orElseGet(this::getOrCreateAlgorithm));
    }

    @Transactional
    public AlgorithmSettingResponse updateAlgorithm(AlgorithmSettingRequest request) {
        validateAlgorithmRange(request);
        AlgoConfig snapshot = getOrCreateAlgorithm();
        snapshot.setTask(null);
        snapshot.setBeeCount(request.bee());
        snapshot.setMaxIter(request.maxIter());
        snapshot.setLimit(request.limit());
        snapshot.setArchiveSize(request.archiveSize());
        snapshot.setTournamentSize(request.tournamentSize());
        snapshot.setEliteRate(request.eliteRate());
        snapshot.setEliminationRate(request.eliminationRate());
        snapshot.setArchiveGuidanceRate(request.archiveGuidanceRate());
        snapshot.touch();
        return AlgorithmSettingResponse.from(algoConfigRepository.save(snapshot));
    }

    @Transactional
    public AlgorithmSettingResponse saveTaskAlgorithm(DispatchTask task, AlgorithmSettingRequest request) {
        validateAlgorithmRange(request);
        AlgoConfig snapshot = copyAlgoConfig(getOrCreateAlgorithm());
        snapshot.setTask(task);
        snapshot.setBeeCount(request.bee());
        snapshot.setMaxIter(request.maxIter());
        snapshot.setLimit(request.limit());
        snapshot.setArchiveSize(request.archiveSize());
        snapshot.setTournamentSize(request.tournamentSize());
        snapshot.setEliteRate(request.eliteRate());
        snapshot.setEliminationRate(request.eliminationRate());
        snapshot.setArchiveGuidanceRate(request.archiveGuidanceRate());
        snapshot.touch();
        return AlgorithmSettingResponse.from(algoConfigRepository.save(snapshot));
    }

    public void validateRange(SettingRequest request) {
        if (request.microTurbineMinKw() < 0
                || request.microTurbineMaxKw() <= 0
                || request.microTurbineRampUpKw() < 0
                || request.microTurbineRampDownKw() < 0
                || request.microTurbineUnitCost() < 0
                || request.batteryCapacityKwh() <= 0
                || request.batteryChargeMaxKw() < 0
                || request.batteryDischargeMaxKw() < 0
                || request.gridBuyMaxKw() < 0
                || request.gridSellMaxKw() < 0
                || request.renewableCurtailmentCost() < 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "设备参数必须为有效非负数，容量和上限必须大于 0");
        }
        if (request.batterySocMin() < 0 || request.batterySocMax() > 1 || request.batterySocInitial() < 0 || request.batterySocInitial() > 1) {
            throw new AppException(HttpStatus.BAD_REQUEST, "SOC 参数必须位于 0 到 1 之间");
        }
        if (request.microTurbineMinKw() > request.microTurbineMaxKw()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "微型燃气轮机下限不能大于上限");
        }
        if (request.batterySocMin() >= request.batterySocMax()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "SOC 下限必须小于上限");
        }
        if (request.batterySocInitial() < request.batterySocMin()
                || request.batterySocInitial() > request.batterySocMax()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "初始 SOC 必须位于上下限之间");
        }
    }

    public void validateAlgorithmRange(AlgorithmSettingRequest request) {
        if (request.bee() <= 0 || request.maxIter() <= 0 || request.limit() <= 0 || request.archiveSize() <= 0 || request.tournamentSize() <= 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "MOIABC 运行配置必须为正整数");
        }
        if (request.bee() > 1000 || request.maxIter() > 10000 || request.limit() > 10000 || request.archiveSize() > 5000 || request.tournamentSize() > 1000) {
            throw new AppException(HttpStatus.BAD_REQUEST, "MOIABC 运行配置超出允许范围");
        }
        if (request.tournamentSize() > request.bee()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "锦标赛规模不能大于蜂群规模");
        }
        if (!isRate(request.eliteRate()) || !isRate(request.eliminationRate()) || !isRate(request.archiveGuidanceRate())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "MOIABC 比例参数必须位于 0 到 1 之间");
        }
    }

    private boolean isRate(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0.0 && value <= 1.0;
    }

    private DeviceParam copyDeviceParam(DeviceParam source) {
        DeviceParam copy = new DeviceParam();
        copy.setMicroTurbineMinKw(source.getMicroTurbineMinKw());
        copy.setMicroTurbineMaxKw(source.getMicroTurbineMaxKw());
        copy.setMicroTurbineRampUpKw(source.getMicroTurbineRampUpKw());
        copy.setMicroTurbineRampDownKw(source.getMicroTurbineRampDownKw());
        copy.setMicroTurbineUnitCost(source.getMicroTurbineUnitCost());
        copy.setBatteryCapacityKwh(source.getBatteryCapacityKwh());
        copy.setBatteryChargeMaxKw(source.getBatteryChargeMaxKw());
        copy.setBatteryDischargeMaxKw(source.getBatteryDischargeMaxKw());
        copy.setBatterySocMin(source.getBatterySocMin());
        copy.setBatterySocMax(source.getBatterySocMax());
        copy.setBatterySocInitial(source.getBatterySocInitial());
        copy.setGridBuyMaxKw(source.getGridBuyMaxKw());
        copy.setGridSellMaxKw(source.getGridSellMaxKw());
        copy.setRenewableCurtailmentCost(source.getRenewableCurtailmentCost());
        return copy;
    }

    private AlgoConfig copyAlgoConfig(AlgoConfig source) {
        AlgoConfig copy = new AlgoConfig();
        copy.setBeeCount(source.getBeeCount());
        copy.setMaxIter(source.getMaxIter());
        copy.setLimit(source.getLimit());
        copy.setArchiveSize(source.getArchiveSize());
        copy.setTournamentSize(source.getTournamentSize());
        copy.setEliteRate(source.getEliteRate());
        copy.setEliminationRate(source.getEliminationRate());
        copy.setArchiveGuidanceRate(source.getArchiveGuidanceRate());
        return copy;
    }

    private AlgoConfig getOrCreateAlgorithm() {
        return algoConfigRepository.findById(DEFAULT_ROW_ID)
                .orElseGet(() -> algoConfigRepository.save(new AlgoConfig()));
    }
}
