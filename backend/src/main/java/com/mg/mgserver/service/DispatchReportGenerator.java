package com.mg.mgserver.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mg.mgserver.service.DispatchTaskService.ExpandedReportData;
import com.mg.mgserver.service.DispatchTaskService.ExpandedReportOption;
import tools.jackson.databind.JsonNode;

final class DispatchReportGenerator {
    private static final int WIDTH = 1240;
    private static final int HEIGHT = 1754;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DecimalFormat TWO = new DecimalFormat("0.00");

    List<BufferedImage> renderPages(ExpandedReportData data) {
        List<BufferedImage> pages = new ArrayList<>();
        pages.add(renderOverviewPage(data));
        pages.add(renderInputParamsPageV2(data));
        pages.add(renderInputChartsPageV2(data));
        pages.add(renderResultChartsPageV2(data));
        pages.addAll(renderOptionPagesV2(data));
        return pages;
    }

    private BufferedImage renderOverviewPage(ExpandedReportData data) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = prepare(image);
        try {
            int left = 70;
            int right = WIDTH - 70;
            int y = 96;
            y = header(g, left, y, right, "调度报告", "任务 #" + data.task().getId() + " / " + LocalDateTime.now().format(TIME_FORMAT));
            y += 20;
            y = keyValueBlock(g, left, y, right - left, "任务信息", List.of(
                    new KV("任务名称", text(data.task().getName())),
                    new KV("任务状态", text(data.task().getStatus().name())),
                    new KV("原始文件", text(resolveOriginalFilename(data.task().getId()))),
                    new KV("创建时间", formatTime(data.task().getCreatedAt())),
                    new KV("开始时间", formatTime(data.task().getStartTime())),
                    new KV("完成时间", formatTime(data.task().getCompletedAt()))
            ));
            y += 24;
            metricGrid(g, left, y, right - left, List.of(
                    new Metric("经济成本", formatDouble(data.result().getEconomicCost()) + " 元", "折中最优方案", new Color(44, 126, 115)),
                    new Metric("环境成本", formatDouble(data.result().getEnvironmentCost()), "折中最优方案", new Color(62, 93, 168)),
                    new Metric("可再生利用率", formatPercent(data.result().getRenewableUtilizationRate()), "结果统计", new Color(39, 115, 148)),
                    new Metric("最终 SOC", formatPercent(data.result().getFinalSoc()), "末端电池状态", new Color(160, 105, 47)),
                    new Metric("当前方案", schemeLabel(data.result().getCurrentScheme()), "方案标识", new Color(111, 88, 170)),
                    new Metric("弃电", summaryText(data.summary(), "total_curtailment_kwh", "0") + " kWh", "总弃电量", new Color(145, 76, 64))
            ));
        } finally {
            g.dispose();
        }
        return image;
    }

    private BufferedImage renderInputParamsPageV2(ExpandedReportData data) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = prepare(image);
        try {
            int left = 70;
            int right = WIDTH - 70;
            int y = 96;
            y = header(g, left, y, right, "\u8f93\u5165\u53c2\u6570", "\u672c\u6b21\u4efb\u52a1\u7684\u7b97\u6cd5\u548c\u8bbe\u5907\u53c2\u6570");
            y += 20;
            int gap = 18;
            int blockWidth = (right - left - gap) / 2;
            int leftBottom = keyValueBlock(g, left, y, blockWidth, "\u7b97\u6cd5\u53c2\u6570", List.of(
                    new KV("\u8717\u7fa4\u6570", String.valueOf(data.algorithmSetting().bee())),
                    new KV("\u6700\u5927\u8fed\u4ee3", String.valueOf(data.algorithmSetting().maxIter())),
                    new KV("\u9650\u5236\u6b21\u6570", String.valueOf(data.algorithmSetting().limit())),
                    new KV("\u6863\u6848\u89c4\u6a21", String.valueOf(data.algorithmSetting().archiveSize())),
                    new KV("\u9526\u6807\u8d5b\u89c4\u6a21", String.valueOf(data.algorithmSetting().tournamentSize())),
                    new KV("\u7cbe\u82f1\u7387", formatPercent(data.algorithmSetting().eliteRate())),
                    new KV("\u6dd8\u6c70\u7387", formatPercent(data.algorithmSetting().eliminationRate())),
                    new KV("\u6863\u6848\u5f15\u5bfc\u7387", formatPercent(data.algorithmSetting().archiveGuidanceRate()))
            ));
            int rightBottom = keyValueBlock(g, left + blockWidth + gap, y, blockWidth, "\u8bbe\u5907\u53c2\u6570", List.of(
                    new KV("\u5fae\u578b\u673a\u7ec4\u6700\u5c0f\u529f\u7387", formatDouble(data.deviceSetting().microTurbineMinKw()) + " kW"),
                    new KV("\u5fae\u578b\u673a\u7ec4\u6700\u5927\u529f\u7387", formatDouble(data.deviceSetting().microTurbineMaxKw()) + " kW"),
                    new KV("\u5fae\u578b\u673a\u7ec4\u722c\u5347", formatDouble(data.deviceSetting().microTurbineRampUpKw()) + " kW"),
                    new KV("\u5fae\u578b\u673a\u7ec4\u722c\u964d", formatDouble(data.deviceSetting().microTurbineRampDownKw()) + " kW"),
                    new KV("\u5fae\u578b\u673a\u7ec4\u6210\u672c", formatDouble(data.deviceSetting().microTurbineUnitCost())),
                    new KV("\u7535\u6c60\u5bb9\u91cf", formatDouble(data.deviceSetting().batteryCapacityKwh()) + " kWh"),
                    new KV("\u7535\u6c60\u5145\u7535\u4e0a\u9650", formatDouble(data.deviceSetting().batteryChargeMaxKw()) + " kW"),
                    new KV("\u7535\u6c60\u653e\u7535\u4e0a\u9650", formatDouble(data.deviceSetting().batteryDischargeMaxKw()) + " kW"),
                    new KV("SOC \u4e0b\u9650", formatPercent(data.deviceSetting().batterySocMin())),
                    new KV("SOC \u4e0a\u9650", formatPercent(data.deviceSetting().batterySocMax())),
                    new KV("\u521d\u59cb SOC", formatPercent(data.deviceSetting().batterySocInitial())),
                    new KV("\u7535\u7f51\u8d2d\u7535\u4e0a\u9650", formatDouble(data.deviceSetting().gridBuyMaxKw()) + " kW"),
                    new KV("\u7535\u7f51\u552e\u7535\u4e0a\u9650", formatDouble(data.deviceSetting().gridSellMaxKw()) + " kW"),
                    new KV("\u5f02\u5e38\u6253\u8d39", formatDouble(data.deviceSetting().renewableCurtailmentCost()))
            ));
            y = Math.max(leftBottom, rightBottom) + 24;
            keyValueBlock(g, left, y, right - left, "\u4efb\u52a1\u4fe1\u606f", List.of(
                    new KV("\u4efb\u52a1\u540d\u79f0", text(data.task().getName())),
                    new KV("\u539f\u59cb\u6587\u4ef6", text(resolveOriginalFilename(data.task().getId()))),
                    new KV("\u521b\u5efa\u65f6\u95f4", formatTime(data.task().getCreatedAt())),
                    new KV("\u5f00\u59cb\u65f6\u95f4", formatTime(data.task().getStartTime())),
                    new KV("\u5b8c\u6210\u65f6\u95f4", formatTime(data.task().getCompletedAt()))
            ));
        } finally {
            g.dispose();
        }
        return image;
    }

    private BufferedImage renderInputChartsPageV2(ExpandedReportData data) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = prepare(image);
        try {
            int left = 70;
            int right = WIDTH - 70;
            int y = 96;
            y = header(g, left, y, right, "\u8f93\u5165\u6570\u636e", "\u9884\u6d4b\u6570\u636e\u66f2\u7ebf / " + text(resolveOriginalFilename(data.task().getId())));
            y += 20;
            y = chartPanel(g, left, y, right - left, 700, null, renderLineChart("\u98ce\u7535\u3001\u5149\u4f0f\u53ca\u8d1f\u8377\u9884\u6d4b\u66f2\u7ebf", "\u529f\u7387 / kW", data.profiles().size(), List.of(
                    new LineSeries("\u8d1f\u8377", new Color(38, 50, 65), data.profiles().stream().map(ProfilePoint::loadKw).toList()),
                    new LineSeries("\u5149\u4f0f", new Color(242, 166, 90), data.profiles().stream().map(ProfilePoint::pvKw).toList()),
                    new LineSeries("\u98ce\u673a", new Color(118, 183, 178), data.profiles().stream().map(ProfilePoint::wtKw).toList())
            ), 1068, 630, 0));
            y += 18;
            chartPanel(g, left, y, right - left, 700, null, renderLineChart("\u5206\u65f6\u8d2d\u552e\u7535\u4ef7\u683c\u66f2\u7ebf", "\u7535\u4ef7", data.profiles().size(), List.of(
                    new LineSeries("\u8d2d\u7535\u4ef7", new Color(78, 121, 167), data.profiles().stream().map(ProfilePoint::buyPrice).toList()),
                    new LineSeries("\u552e\u7535\u4ef7", new Color(242, 166, 90), data.profiles().stream().map(ProfilePoint::sellPrice).toList())
            ), 1068, 630, 2));
        } finally {
            g.dispose();
        }
        return image;
    }

    private BufferedImage renderResultChartsPageV2(ExpandedReportData data) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = prepare(image);
        try {
            int left = 70;
            int right = WIDTH - 70;
            int y = 96;
            String scheme = schemeLabel(data.result().getCurrentScheme());
            y = header(g, left, y, right, "\u8c03\u5ea6\u7ed3\u679c", scheme);
            y += 20;
            y = chartPanel(g, left, y, right - left, 700, null, renderBalanceChartLarge(data.dispatchCurves()));
            y += 18;
            chartPanel(g, left, y, right - left, 700, null, renderStorageChartLarge(data.dispatchCurves()));
        } finally {
            g.dispose();
        }
        return image;
    }

    private List<BufferedImage> renderOptionPagesV2(ExpandedReportData data) {
        List<BufferedImage> pages = new ArrayList<>();
        pages.addAll(renderTablePagesV2(
                "\u65b9\u6848\u5bf9\u6bd4",
                "Pareto \u524d\u6cbf",
                "Pareto \u524d\u6cbf",
                List.of("\u540d\u79f0", "\u8bf4\u660e", "\u7ecf\u6d4e\u6210\u672c", "\u73af\u5883\u6210\u672c", "\u60e9\u7f5a", "\u6700\u7ec8 SOC", "\u53ef\u518d\u751f\u5229\u7528\u7387"),
                optionRows(data.paretoFront())
        ));
        return pages;
    }

    private List<BufferedImage> renderTablePagesV2(String pageTitle, String subtitle, String tableTitle, List<String> headers, List<List<String>> rows) {
        List<BufferedImage> pages = new ArrayList<>();
        if (rows.isEmpty()) {
            return pages;
        }
        int left = 70;
        int right = WIDTH - 70;
        int headerTop = 96;
        int contentTop = headerTop + 82 + 20;
        int maxRows = Math.max(1, (HEIGHT - contentTop - 120) / 36 - 1);
        for (int start = 0; start < rows.size(); start += maxRows) {
            BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = prepare(image);
            try {
                header(g, left, headerTop, right, pageTitle, subtitle);
                int y = contentTop;
                int end = Math.min(rows.size(), start + maxRows);
                String currentTitle = start == 0 ? tableTitle : tableTitle + " (cont.)";
                table(g, left, y, right - left, currentTitle, headers, rows.subList(start, end));
            } finally {
                g.dispose();
            }
            pages.add(image);
        }
        return pages;
    }

    private BufferedImage renderInputChartsPage(ExpandedReportData data) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = prepare(image);
        try {
            int left = 70;
            int right = WIDTH - 70;
            int y = 96;
            y = header(g, left, y, right, "杈撳叆鏁版嵁", "棰勬祴鏁版嵁鏇茬嚎 / " + text(resolveOriginalFilename(data.task().getId())));
            y += 20;
            y = chartPanel(g, left, y, right - left, 700, "椋庣數銆佸厜浼忓強璐熻嵎棰勬祴鏇茬嚎", renderLineChart("椋庣數銆佸厜浼忓強璐熻嵎棰勬祴鏇茬嚎", "鍔熺巼 / kW", data.profiles().size(), List.of(
                    new LineSeries("璐熻嵎", new Color(38, 50, 65), data.profiles().stream().map(ProfilePoint::loadKw).toList()),
                    new LineSeries("鍏変紡", new Color(242, 166, 90), data.profiles().stream().map(ProfilePoint::pvKw).toList()),
                    new LineSeries("椋庢満", new Color(118, 183, 178), data.profiles().stream().map(ProfilePoint::wtKw).toList())
            ), 1068, 630, 0));
            y += 18;
            chartPanel(g, left, y, right - left, 700, "鍒嗘椂璐敭鐢典环鏍兼洸绾?", renderLineChart("鍒嗘椂璐敭鐢典环鏍兼洸绾?", "鐢典环", data.profiles().size(), List.of(
                    new LineSeries("璐數浠?", new Color(78, 121, 167), data.profiles().stream().map(ProfilePoint::buyPrice).toList()),
                    new LineSeries("鍞數浠?", new Color(242, 166, 90), data.profiles().stream().map(ProfilePoint::sellPrice).toList())
            ), 1068, 630, 2));
        } finally {
            g.dispose();
        }
        return image;
    }

    private BufferedImage renderResultChartsPage(ExpandedReportData data) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = prepare(image);
        try {
            int left = 70;
            int right = WIDTH - 70;
            int y = 96;
            String scheme = schemeLabel(data.result().getCurrentScheme());
            y = header(g, left, y, right, "璋冨害缁撴灉", scheme);
            y += 20;
            y = chartPanel(g, left, y, right - left, 700, "鍔熺巼骞宠?", renderBalanceChart(data.dispatchCurves()));
            y += 18;
            chartPanel(g, left, y, right - left, 700, "鍌ㄨ兘鍔熺巼涓庤嵎鐢电姸鎬?", renderStorageChart(data.dispatchCurves()));
        } finally {
            g.dispose();
        }
        return image;
    }

    private List<BufferedImage> renderOptionPages(ExpandedReportData data) {
        List<BufferedImage> pages = new ArrayList<>();
        pages.addAll(renderTablePages(
                "鏂规瀵规瘮",
                "鎶樹腑鏈€浼樻柟妗堝凡浣滀负褰撳墠缁撴灉",
                "褰撳墠鏂规鍒楄〃",
                List.of("鏂规", "璇存槑", "缁忔祹鎴愭湰", "鐜鎴愭湰", "鎯╃綒", "鏈€缁?SOC", "鍙啀鐢熷埄鐢ㄧ巼"),
                optionRows(data.solutionOptions())
        ));
        pages.addAll(renderTablePages(
                "鏂规瀵规瘮",
                "Pareto 鍓嶆部",
                "Pareto 鍓嶆部",
                List.of("鍚嶇О", "璇存槑", "缁忔祹鎴愭湰", "鐜鎴愭湰", "鎯╃綒", "鏈€缁?SOC", "鍙啀鐢熷埄鐢ㄧ巼"),
                optionRows(data.paretoFront())
        ));
        return pages;
    }

    private List<BufferedImage> renderTablePages(String pageTitle, String subtitle, String tableTitle, List<String> headers, List<List<String>> rows) {
        List<BufferedImage> pages = new ArrayList<>();
        if (rows.isEmpty()) {
            return pages;
        }
        int left = 70;
        int right = WIDTH - 70;
        int headerTop = 96;
        int contentTop = headerTop + 82 + 20;
        int maxRows = Math.max(1, (HEIGHT - contentTop - 120) / 36 - 1);
        for (int start = 0; start < rows.size(); start += maxRows) {
            BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = prepare(image);
            try {
                header(g, left, headerTop, right, pageTitle, subtitle);
                int y = contentTop;
                int end = Math.min(rows.size(), start + maxRows);
                String currentTitle = start == 0 ? tableTitle : tableTitle + "（续）";
                table(g, left, y, right - left, currentTitle, headers, rows.subList(start, end));
            } finally {
                g.dispose();
            }
            pages.add(image);
        }
        return pages;
    }

    private BufferedImage renderInputPage(ExpandedReportData data) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = prepare(image);
        try {
            int left = 70;
            int right = WIDTH - 70;
            int y = 96;
            y = header(g, left, y, right, "输入数据", "预测数据曲线 / " + text(resolveOriginalFilename(data.task().getId())));
            y += 20;
            int gap = 18;
            int blockWidth = (right - left - gap) / 2;
            int leftBottom = keyValueBlock(g, left, y, blockWidth, "算法参数", List.of(
                    new KV("蜜蜂数量", String.valueOf(data.algorithmSetting().bee())),
                    new KV("最大迭代", String.valueOf(data.algorithmSetting().maxIter())),
                    new KV("限制次数", String.valueOf(data.algorithmSetting().limit())),
                    new KV("归档规模", String.valueOf(data.algorithmSetting().archiveSize())),
                    new KV("锦标赛规模", String.valueOf(data.algorithmSetting().tournamentSize())),
                    new KV("精英率", formatPercent(data.algorithmSetting().eliteRate())),
                    new KV("淘汰率", formatPercent(data.algorithmSetting().eliminationRate())),
                    new KV("归档引导率", formatPercent(data.algorithmSetting().archiveGuidanceRate()))
            ));
            int rightBottom = keyValueBlock(g, left + blockWidth + gap, y, blockWidth, "设备参数", List.of(
                    new KV("微型机组最小功率", formatDouble(data.deviceSetting().microTurbineMinKw()) + " kW"),
                    new KV("微型机组最大功率", formatDouble(data.deviceSetting().microTurbineMaxKw()) + " kW"),
                    new KV("微型机组爬升", formatDouble(data.deviceSetting().microTurbineRampUpKw()) + " kW"),
                    new KV("微型机组爬降", formatDouble(data.deviceSetting().microTurbineRampDownKw()) + " kW"),
                    new KV("微型机组成本", formatDouble(data.deviceSetting().microTurbineUnitCost())),
                    new KV("电池容量", formatDouble(data.deviceSetting().batteryCapacityKwh()) + " kWh"),
                    new KV("电池充电上限", formatDouble(data.deviceSetting().batteryChargeMaxKw()) + " kW"),
                    new KV("电池放电上限", formatDouble(data.deviceSetting().batteryDischargeMaxKw()) + " kW"),
                    new KV("SOC 下限", formatPercent(data.deviceSetting().batterySocMin())),
                    new KV("SOC 上限", formatPercent(data.deviceSetting().batterySocMax())),
                    new KV("初始 SOC", formatPercent(data.deviceSetting().batterySocInitial())),
                    new KV("电网购电上限", formatDouble(data.deviceSetting().gridBuyMaxKw()) + " kW"),
                    new KV("电网售电上限", formatDouble(data.deviceSetting().gridSellMaxKw()) + " kW"),
                    new KV("弃电成本", formatDouble(data.deviceSetting().renewableCurtailmentCost()))
            ));
            y = Math.max(leftBottom, rightBottom) + 24;
            y = chartPanel(g, left, y, right - left, 330, "风电、光伏及负荷预测曲线", renderLineChart("风电、光伏及负荷预测曲线", "功率 / kW", data.profiles().size(), List.of(
                    new LineSeries("负荷", new Color(38, 50, 65), data.profiles().stream().map(ProfilePoint::loadKw).toList()),
                    new LineSeries("光伏", new Color(242, 166, 90), data.profiles().stream().map(ProfilePoint::pvKw).toList()),
                    new LineSeries("风机", new Color(118, 183, 178), data.profiles().stream().map(ProfilePoint::wtKw).toList())
            ), 1000, 430, 0));
            y += 18;
            chartPanel(g, left, y, right - left, 300, "分时购售电价格曲线", renderLineChart("分时购售电价格曲线", "电价", data.profiles().size(), List.of(
                    new LineSeries("购电价", new Color(78, 121, 167), data.profiles().stream().map(ProfilePoint::buyPrice).toList()),
                    new LineSeries("售电价", new Color(242, 166, 90), data.profiles().stream().map(ProfilePoint::sellPrice).toList())
            ), 1000, 430, 2));
        } finally {
            g.dispose();
        }
        return image;
    }

    private BufferedImage renderResultPage(ExpandedReportData data) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = prepare(image);
        try {
            int left = 70;
            int right = WIDTH - 70;
            int y = 96;
            String scheme = schemeLabel(data.result().getCurrentScheme());
            y = header(g, left, y, right, "调度结果", scheme);
            y += 18;
            metricGrid(g, left, y, right - left, List.of(
                    new Metric("经济成本", formatDouble(data.result().getEconomicCost()) + " 元", "折中方案", new Color(44, 126, 115)),
                    new Metric("环境成本", formatDouble(data.result().getEnvironmentCost()), "折中方案", new Color(62, 93, 168)),
                    new Metric("可再生利用率", formatPercent(data.result().getRenewableUtilizationRate()), "结果统计", new Color(39, 115, 148)),
                    new Metric("最终 SOC", formatPercent(data.result().getFinalSoc()), "末端电池状态", new Color(160, 105, 47)),
                    new Metric("当前方案", scheme, "方案标识", new Color(111, 88, 170)),
                    new Metric("弃电", summaryText(data.summary(), "total_curtailment_kwh", "0") + " kWh", "总弃电量", new Color(145, 76, 64))
            ));
            y += 24;
            y = chartPanel(g, left, y, right - left, 360, "功率平衡", renderBalanceChart(data.dispatchCurves()));
            y += 18;
            chartPanel(g, left, y, right - left, 330, "储能功率与荷电状态", renderStorageChart(data.dispatchCurves()));
        } finally {
            g.dispose();
        }
        return image;
    }

    private BufferedImage renderOptionPage(ExpandedReportData data) {
        if (data.solutionOptions().isEmpty() && data.paretoFront().isEmpty()) {
            return null;
        }
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = prepare(image);
        try {
            int left = 70;
            int right = WIDTH - 70;
            int y = 96;
            y = header(g, left, y, right, "方案对比", "折中最优方案已作为当前结果");
            y += 20;
            y = table(g, left, y, right - left, "当前方案列表", List.of("方案", "说明", "经济成本", "环境成本", "惩罚", "最终 SOC", "可再生利用率"), optionRows(data.solutionOptions()));
            y += 24;
            table(g, left, y, right - left, "Pareto 前沿", List.of("名称", "说明", "经济成本", "环境成本", "惩罚", "最终 SOC", "可再生利用率"), optionRows(data.paretoFront()));
        } finally {
            g.dispose();
        }
        return image;
    }

    private BufferedImage renderLineChart(String title, String yTitle, int count, List<LineSeries> series, int width, int height, int digits) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = prepare(image);
        try {
            int left = 64;
            int right = 34;
            int top = 24;
            int bottom = 54;
            int plotWidth = width - left - right;
            int plotHeight = height - top - bottom;
            double max = niceCeil(Math.max(1.0, max(series.stream().flatMap(s -> s.values().stream()).toList())));
            double step = plotWidth / Math.max(Math.max(count - 1, 1), 1);
            g.setFont(font(Font.BOLD, 28));
            g.setColor(new Color(31, 41, 55));
            g.drawString(title, left, 20);
            frame(g, left, top, plotWidth, plotHeight);
            grid(g, left, top, plotWidth, plotHeight, 4);
            g.setFont(font(Font.PLAIN, 16));
            g.setColor(new Color(75, 85, 99));
            g.drawString(yTitle, 18, top + plotHeight / 2);
            g.drawString("时间 / h", width / 2 - 30, height - 10);
            for (double tick : new double[]{0, 4, 8, 12, 16, 20, 24}) {
                double x = left + tick / 24.0 * plotWidth;
                g.drawLine((int) x, top, (int) x, top + plotHeight);
                g.drawString(String.valueOf((int) tick), (int) x - 4, height - 18);
            }
            for (LineSeries line : series) {
                List<String> points = new ArrayList<>();
                for (int i = 0; i < line.values().size(); i++) {
                    double x = left + i * step;
                    double y = top + (1 - line.values().get(i) / max) * plotHeight;
                    points.add(point(x, y));
                }
                polyline(g, points, line.color());
                for (int i = 0; i < line.values().size(); i++) {
                    double x = left + i * step;
                    double y = top + (1 - line.values().get(i) / max) * plotHeight;
                    g.setColor(line.color());
                    g.fill(new java.awt.geom.Ellipse2D.Double(x - 4.2, y - 4.2, 8.4, 8.4));
                }
            }
            for (double label : new double[]{max, max * 0.5, 0}) {
                double y = top + (1 - label / max) * plotHeight;
                g.setColor(new Color(107, 114, 128));
                g.drawString(formatNumber(label, digits), 38, (int) (y + 5));
            }
            legend(g, 54, height - 20, series.stream().map(line -> new Legend(line.label(), line.color())).toList());
        } finally {
            g.dispose();
        }
        return image;
    }

    private BufferedImage renderBalanceChart(List<DispatchCurvePoint> curves) {
        BufferedImage image = new BufferedImage(1000, 470, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = prepare(image);
        try {
            int left = 64;
            int right = 34;
            int top = 30;
            int bottom = 56;
            int width = 1000 - left - right;
            int height = 470 - top - bottom;
            List<Double> load = curves.stream().map(DispatchCurvePoint::loadKw).toList();
            List<Double> positiveTotals = curves.stream().map(p -> p.wtKw() + p.pvKw() + p.microTurbineKw() + p.gridBuyKw() + Math.max(0.0, p.batteryKw())).toList();
            List<Double> negativeTotals = curves.stream().map(p -> Math.max(0.0, -p.batteryKw()) + p.gridSellKw()).toList();
            double maxPositive = niceCeil(Math.max(1.0, Math.max(max(load), max(positiveTotals))));
            double maxNegative = niceCeil(Math.max(1.0, max(negativeTotals)));
            double zeroY = top + height * (maxPositive / (maxPositive + maxNegative));
            double scale = height / (maxPositive + maxNegative);
            double step = width / Math.max(curves.size(), 1);
            double barWidth = Math.max(6, step * 0.72);
            g.setFont(font(Font.BOLD, 28));
            g.setColor(new Color(31, 41, 55));
            g.drawString("功率平衡", left, 22);
            frame(g, left, top, width, height);
            grid(g, left, top, width, height, 4);
            List<String> loadPoints = new ArrayList<>();
            for (int i = 0; i < curves.size(); i++) {
                DispatchCurvePoint p = curves.get(i);
                double x = left + (i + 0.5) * step;
                double y = zeroY - p.loadKw() * scale;
                loadPoints.add(point(x, y));
                g.setColor(new Color(47, 90, 171));
                g.fill(new java.awt.geom.Ellipse2D.Double(x - 4, y - 4, 8, 8));
            }
            polyline(g, loadPoints, new Color(47, 90, 171));
            for (int i = 0; i < curves.size(); i++) {
                DispatchCurvePoint p = curves.get(i);
                double x = left + i * step + (step - barWidth) / 2.0;
                double yTop = zeroY;
                double[] values = {p.wtKw(), p.pvKw(), p.microTurbineKw(), p.gridBuyKw(), Math.max(0.0, p.batteryKw())};
                Color[] colors = {new Color(78, 121, 167), new Color(242, 193, 78), new Color(156, 122, 82), new Color(89, 161, 79), new Color(225, 87, 89)};
                for (int j = 0; j < values.length; j++) {
                    if (values[j] <= 0) continue;
                    double h = values[j] * scale;
                    yTop -= h;
                    g.setColor(colors[j]);
                    g.fill(new RoundRectangle2D.Double(x, yTop, barWidth, h, 2, 2));
                }
                double[] neg = {Math.max(0.0, -p.batteryKw()), p.gridSellKw()};
                Color[] negColors = {new Color(175, 122, 161), new Color(118, 183, 178)};
                double yBottom = zeroY;
                for (int j = 0; j < neg.length; j++) {
                    if (neg[j] <= 0) continue;
                    double h = neg[j] * scale;
                    g.setColor(negColors[j]);
                    g.fill(new RoundRectangle2D.Double(x, yBottom, barWidth, h, 2, 2));
                    yBottom += h;
                }
            }
            axis(g, left, top, width, height, zeroY, new double[]{0, 4, 8, 12, 16, 20, 24}, true);
            legend(g, 54, 420, List.of(
                    new Legend("负荷", new Color(47, 90, 171)),
                    new Legend("风机", new Color(78, 121, 167)),
                    new Legend("光伏", new Color(242, 193, 78)),
                    new Legend("微型燃气轮机", new Color(156, 122, 82)),
                    new Legend("主网购电", new Color(89, 161, 79)),
                    new Legend("储能放电", new Color(225, 87, 89)),
                    new Legend("储能充电", new Color(175, 122, 161)),
                    new Legend("主网售电", new Color(118, 183, 178))
            ));
        } finally {
            g.dispose();
        }
        return image;
    }

    private BufferedImage renderBalanceChartLarge(List<DispatchCurvePoint> curves) {
        BufferedImage image = new BufferedImage(1120, 630, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = prepare(image);
        try {
            int left = 70;
            int right = 42;
            int top = 34;
            int bottom = 72;
            int width = image.getWidth() - left - right;
            int height = image.getHeight() - top - bottom;
            List<Double> load = curves.stream().map(DispatchCurvePoint::loadKw).toList();
            List<Double> positiveTotals = curves.stream().map(p -> p.wtKw() + p.pvKw() + p.microTurbineKw() + p.gridBuyKw() + Math.max(0.0, p.batteryKw())).toList();
            List<Double> negativeTotals = curves.stream().map(p -> Math.max(0.0, -p.batteryKw()) + p.gridSellKw()).toList();
            double maxPositive = niceCeil(Math.max(1.0, Math.max(max(load), max(positiveTotals))));
            double maxNegative = niceCeil(Math.max(1.0, max(negativeTotals)));
            double zeroY = top + height * (maxPositive / (maxPositive + maxNegative));
            double scale = height / (maxPositive + maxNegative);
            double step = width / Math.max(curves.size(), 1);
            double barWidth = Math.max(6, step * 0.72);
            g.setFont(font(Font.BOLD, 28));
            g.setColor(new Color(31, 41, 55));
            g.drawString("\u529f\u7387\u5e73\u8861", left, 22);
            frame(g, left, top, width, height);
            grid(g, left, top, width, height, 4);
            List<String> loadPoints = new ArrayList<>();
            for (int i = 0; i < curves.size(); i++) {
                DispatchCurvePoint p = curves.get(i);
                double x = left + (i + 0.5) * step;
                double y = zeroY - p.loadKw() * scale;
                loadPoints.add(point(x, y));
                g.setColor(new Color(47, 90, 171));
                g.fill(new java.awt.geom.Ellipse2D.Double(x - 4, y - 4, 8, 8));
            }
            polyline(g, loadPoints, new Color(47, 90, 171));
            for (int i = 0; i < curves.size(); i++) {
                DispatchCurvePoint p = curves.get(i);
                double x = left + i * step + (step - barWidth) / 2.0;
                double yTop = zeroY;
                double[] values = {p.wtKw(), p.pvKw(), p.microTurbineKw(), p.gridBuyKw(), Math.max(0.0, p.batteryKw())};
                Color[] colors = {new Color(78, 121, 167), new Color(242, 193, 78), new Color(156, 122, 82), new Color(89, 161, 79), new Color(225, 87, 89)};
                for (int j = 0; j < values.length; j++) {
                    if (values[j] <= 0) continue;
                    double h = values[j] * scale;
                    yTop -= h;
                    g.setColor(colors[j]);
                    g.fill(new RoundRectangle2D.Double(x, yTop, barWidth, h, 2, 2));
                }
                double[] neg = {Math.max(0.0, -p.batteryKw()), p.gridSellKw()};
                Color[] negColors = {new Color(175, 122, 161), new Color(118, 183, 178)};
                double yBottom = zeroY;
                for (int j = 0; j < neg.length; j++) {
                    if (neg[j] <= 0) continue;
                    double h = neg[j] * scale;
                    g.setColor(negColors[j]);
                    g.fill(new RoundRectangle2D.Double(x, yBottom, barWidth, h, 2, 2));
                    yBottom += h;
                }
            }
            axis(g, left, top, width, height, zeroY, new double[]{0, 4, 8, 12, 16, 20, 24}, true);
            legend(g, 54, image.getHeight() - 18, List.of(
                    new Legend("\u8d1f\u8377", new Color(47, 90, 171)),
                    new Legend("\u98ce\u673a", new Color(78, 121, 167)),
                    new Legend("\u5149\u4f0f", new Color(242, 193, 78)),
                    new Legend("\u5fae\u578b\u71c3\u6c14\u8f6e\u673a", new Color(156, 122, 82)),
                    new Legend("\u4e3b\u7f51\u8d2d\u7535", new Color(89, 161, 79)),
                    new Legend("\u50a8\u80fd\u653e\u7535", new Color(225, 87, 89)),
                    new Legend("\u50a8\u80fd\u5145\u7535", new Color(175, 122, 161)),
                    new Legend("\u4e3b\u7f51\u552e\u7535", new Color(118, 183, 178))
            ));
        } finally {
            g.dispose();
        }
        return image;
    }

    private BufferedImage renderStorageChart(List<DispatchCurvePoint> curves) {
        BufferedImage image = new BufferedImage(1000, 430, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = prepare(image);
        try {
            int left = 64;
            int right = 58;
            int top = 28;
            int bottom = 52;
            int width = 1000 - left - right;
            int height = 430 - top - bottom;
            double maxPower = niceCeil(Math.max(1.0, curves.stream().flatMapToDouble(p -> java.util.stream.DoubleStream.of(Math.abs(p.batteryKw()), Math.max(0.0, p.batteryKw()), Math.max(0.0, -p.batteryKw()))).max().orElse(1.0)));
            double zeroY = top + height / 2.0;
            double scale = (height / 2.0) / maxPower;
            double step = width / Math.max(curves.size(), 1);
            double barWidth = Math.max(6, step * 0.72);
            g.setFont(font(Font.BOLD, 28));
            g.setColor(new Color(31, 41, 55));
            g.drawString("储能功率与荷电状态", left, 22);
            frame(g, left, top, width, height);
            grid(g, left, top, width, height, 4);
            g.setFont(font(Font.PLAIN, 14));
            g.setColor(new Color(107, 114, 128));
            for (double tick : new double[]{maxPower, maxPower * 0.5, 0, -maxPower * 0.5, -maxPower}) {
                double y = zeroY - tick * scale;
                g.drawString(formatDouble(tick), 8, (int) (y + 5));
            }
            List<String> netPoints = new ArrayList<>();
            List<String> socPoints = new ArrayList<>();
            for (int i = 0; i < curves.size(); i++) {
                DispatchCurvePoint p = curves.get(i);
                double x = left + (i + 0.5) * step;
                double netY = zeroY - p.batteryKw() * scale;
                double socY = top + (1 - normalizeSoc(p.soc())) * height;
                netPoints.add(point(x, netY));
                socPoints.add(point(x, socY));
                double discharge = Math.max(0.0, p.batteryKw()) * scale;
                if (discharge > 0) {
                    g.setColor(new Color(225, 87, 89));
                    g.fill(new RoundRectangle2D.Double(x - barWidth / 2.0, zeroY - discharge, barWidth, discharge, 2, 2));
                }
                double charge = Math.max(0.0, -p.batteryKw()) * scale;
                if (charge > 0) {
                    g.setColor(new Color(175, 122, 161));
                    g.fill(new RoundRectangle2D.Double(x - barWidth / 2.0, zeroY, barWidth, charge, 2, 2));
                }
            }
            polyline(g, netPoints, new Color(47, 90, 171));
            polyline(g, socPoints, new Color(44, 165, 141));
            axis(g, left, top, width, height, zeroY, new double[]{0, 4, 8, 12, 16, 20, 24}, true);
            legend(g, 54, 400, List.of(
                    new Legend("储能净功率", new Color(47, 90, 171)),
                    new Legend("储能放电", new Color(225, 87, 89)),
                    new Legend("储能充电", new Color(175, 122, 161)),
                    new Legend("荷电状态", new Color(44, 165, 141))
            ));
        } finally {
            g.dispose();
        }
        return image;
    }

    private BufferedImage renderStorageChartLarge(List<DispatchCurvePoint> curves) {
        BufferedImage image = new BufferedImage(1120, 630, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = prepare(image);
        try {
            int left = 70;
            int right = 56;
            int top = 34;
            int bottom = 72;
            int width = image.getWidth() - left - right;
            int height = image.getHeight() - top - bottom;
            double maxPower = niceCeil(Math.max(1.0, curves.stream().flatMapToDouble(p -> java.util.stream.DoubleStream.of(Math.abs(p.batteryKw()), Math.max(0.0, p.batteryKw()), Math.max(0.0, -p.batteryKw()))).max().orElse(1.0)));
            double zeroY = top + height / 2.0;
            double scale = (height / 2.0) / maxPower;
            double step = width / Math.max(curves.size(), 1);
            double barWidth = Math.max(6, step * 0.72);
            g.setFont(font(Font.BOLD, 28));
            g.setColor(new Color(31, 41, 55));
            g.drawString("\u50a8\u80fd\u529f\u7387\u4e0e\u8377\u7535\u72b6\u6001", left, 22);
            frame(g, left, top, width, height);
            grid(g, left, top, width, height, 4);
            g.setFont(font(Font.PLAIN, 14));
            g.setColor(new Color(107, 114, 128));
            for (double tick : new double[]{maxPower, maxPower * 0.5, 0, -maxPower * 0.5, -maxPower}) {
                double y = zeroY - tick * scale;
                g.drawString(formatDouble(tick), 8, (int) (y + 5));
            }
            List<String> netPoints = new ArrayList<>();
            List<String> socPoints = new ArrayList<>();
            for (int i = 0; i < curves.size(); i++) {
                DispatchCurvePoint p = curves.get(i);
                double x = left + (i + 0.5) * step;
                double netY = zeroY - p.batteryKw() * scale;
                double socY = top + (1 - normalizeSoc(p.soc())) * height;
                netPoints.add(point(x, netY));
                socPoints.add(point(x, socY));
                double discharge = Math.max(0.0, p.batteryKw()) * scale;
                if (discharge > 0) {
                    g.setColor(new Color(225, 87, 89));
                    g.fill(new RoundRectangle2D.Double(x - barWidth / 2.0, zeroY - discharge, barWidth, discharge, 2, 2));
                }
                double charge = Math.max(0.0, -p.batteryKw()) * scale;
                if (charge > 0) {
                    g.setColor(new Color(175, 122, 161));
                    g.fill(new RoundRectangle2D.Double(x - barWidth / 2.0, zeroY, barWidth, charge, 2, 2));
                }
            }
            polyline(g, netPoints, new Color(47, 90, 171));
            polyline(g, socPoints, new Color(44, 165, 141));
            axis(g, left, top, width, height, zeroY, new double[]{0, 4, 8, 12, 16, 20, 24}, true);
            legend(g, 54, image.getHeight() - 18, List.of(
                    new Legend("\u50a8\u80fd\u51c0\u529f\u7387", new Color(47, 90, 171)),
                    new Legend("\u50a8\u80fd\u653e\u7535", new Color(225, 87, 89)),
                    new Legend("\u50a8\u80fd\u5145\u7535", new Color(175, 122, 161)),
                    new Legend("\u8377\u7535\u72b6\u6001", new Color(44, 165, 141))
            ));
        } finally {
            g.dispose();
        }
        return image;
    }

    private int chartPanel(Graphics2D g, int left, int top, int width, int height, String title, BufferedImage chart) {
        g.setColor(new Color(248, 250, 252));
        g.fillRoundRect(left, top, width, height, 18, 18);
        g.setColor(new Color(217, 223, 232));
        g.drawRoundRect(left, top, width, height, 18, 18);
        int chartTop = top + 18;
        if (title != null && !title.isBlank()) {
            g.setColor(new Color(20, 28, 44));
            g.setFont(font(Font.BOLD, 30));
            g.drawString(title, left + 18, top + 34);
            g.setColor(new Color(217, 223, 232));
            g.fillRect(left + 18, top + 44, width - 36, 1);
            chartTop = top + 58;
        }
        g.drawImage(chart, left + 16, chartTop, width - 32, height - (chartTop - top) - 16, null);
        return top + height;
    }

    private int header(Graphics2D g, int left, int top, int right, String title, String subtitle) {
        g.setColor(new Color(20, 28, 44));
        g.setFont(font(Font.BOLD, 54));
        g.drawString(title, left, top);
        if (subtitle != null && !subtitle.isBlank()) {
            g.setColor(new Color(101, 112, 130));
            g.setFont(font(Font.PLAIN, 17));
            g.drawString(subtitle, left, top + 44);
        }
        g.setColor(new Color(20, 28, 44));
        g.fillRect(left, top + 64, right - left, 2);
        return top + 82;
    }

    private int keyValueBlock(Graphics2D g, int left, int top, int width, String title, List<KV> rows) {
        int rowHeight = 40;
        int height = 70 + rows.size() * rowHeight;
        g.setColor(new Color(248, 250, 252));
        g.fillRoundRect(left, top, width, height, 18, 18);
        g.setColor(new Color(217, 223, 232));
        g.drawRoundRect(left, top, width, height, 18, 18);
        g.setColor(new Color(20, 28, 44));
        g.setFont(font(Font.BOLD, 30));
        g.drawString(title, left + 18, top + 36);
        g.setColor(new Color(217, 223, 232));
        g.fillRect(left + 18, top + 50, width - 36, 1);
        int y = top + 86;
        for (int i = 0; i < rows.size(); i++) {
            KV row = rows.get(i);
            if (i > 0) {
                g.setColor(new Color(232, 236, 241));
                g.fillRect(left + 18, y - 14, width - 36, 1);
            }
            g.setColor(new Color(101, 112, 130));
            g.setFont(font(Font.BOLD, 18));
            drawText(g, row.label(), left + 18, y, width / 2 - 36);
            g.setColor(new Color(20, 28, 44));
            g.setFont(font(Font.PLAIN, 18));
            drawText(g, row.value(), left + width / 2 - 10, y, width / 2 - 28);
            y += rowHeight;
        }
        return top + height;
    }

    private void metricGrid(Graphics2D g, int left, int top, int width, List<Metric> metrics) {
        int columns = 3;
        int gap = 16;
        int cardHeight = 110;
        int cardWidth = (width - gap * (columns - 1)) / columns;
        for (int i = 0; i < metrics.size(); i++) {
            Metric metric = metrics.get(i);
            int row = i / columns;
            int col = i % columns;
            int x = left + col * (cardWidth + gap);
            int y = top + row * (cardHeight + gap);
            g.setColor(new Color(245, 247, 250));
            g.fillRoundRect(x, y, cardWidth, cardHeight, 18, 18);
            g.setColor(new Color(217, 223, 232));
            g.drawRoundRect(x, y, cardWidth, cardHeight, 18, 18);
            g.setColor(metric.accent());
            g.fillRoundRect(x, y, 8, cardHeight, 18, 18);
            g.setColor(new Color(101, 112, 130));
            g.setFont(font(Font.BOLD, 20));
            g.drawString(metric.label(), x + 20, y + 30);
            g.setColor(new Color(20, 28, 44));
            g.setFont(font(Font.BOLD, 30));
            drawText(g, metric.value(), x + 20, y + 72, cardWidth - 36);
            g.setColor(new Color(101, 112, 130));
            g.setFont(font(Font.PLAIN, 16));
            drawText(g, metric.note(), x + 20, y + 96, cardWidth - 36);
        }
    }

    private int table(Graphics2D g, int left, int top, int width, String title, List<String> headers, List<List<String>> rows) {
        int rowHeight = 36;
        int height = 70 + (rows.size() + 1) * rowHeight;
        g.setColor(new Color(248, 250, 252));
        g.fillRoundRect(left, top, width, height, 18, 18);
        g.setColor(new Color(217, 223, 232));
        g.drawRoundRect(left, top, width, height, 18, 18);
        g.setColor(new Color(20, 28, 44));
        g.setFont(font(Font.BOLD, 30));
        g.drawString(title, left + 18, top + 36);
        g.setColor(new Color(217, 223, 232));
        g.fillRect(left + 18, top + 50, width - 36, 1);
        int y = top + 82;
        int colWidth = (width - 36) / headers.size();
        g.setFont(font(Font.BOLD, 15));
        g.setColor(new Color(101, 112, 130));
        for (int i = 0; i < headers.size(); i++) {
            drawText(g, headers.get(i), left + 18 + i * colWidth, y, colWidth - 12);
        }
        y += rowHeight;
        g.setColor(new Color(232, 236, 241));
        g.fillRect(left + 18, y - 14, width - 36, 1);
        g.setFont(font(Font.PLAIN, 15));
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                g.setColor(new Color(20, 28, 44));
                drawText(g, row.get(i), left + 18 + i * colWidth, y, colWidth - 12);
            }
            y += rowHeight;
        }
        return top + height;
    }

    private List<List<String>> optionRows(List<ExpandedReportOption> options) {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            ExpandedReportOption option = options.get(i);
            rows.add(List.of(
                    displayName(option, i),
                    text(option.description()),
                    formatDouble(option.economicCost()),
                    formatDouble(option.environmentCost()),
                    formatDouble(option.penalty()),
                    formatPercent(option.finalSoc()),
                    formatPercent(option.renewableUtilizationRate())
            ));
        }
        return rows;
    }

    private String displayName(ExpandedReportOption option, int fallbackIndex) {
        if (option.compromise()) {
            return "折中最优";
        }
        if (option.economicBest()) {
            return "经济最优";
        }
        if (option.environmentBest()) {
            return "环境最优";
        }
        if (option.label() != null && !option.label().isBlank()) {
            return option.label();
        }
        if (option.key() != null && !option.key().isBlank()) {
            return option.key();
        }
        return String.valueOf(fallbackIndex + 1);
    }

    private void drawText(Graphics2D g, String text, int x, int y, int maxWidth) {
        if (text == null) {
            text = "";
        }
        FontMetrics metrics = g.getFontMetrics();
        String value = text;
        while (metrics.stringWidth(value) > maxWidth && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        if (!value.equals(text) && value.length() > 1) {
            value = value.substring(0, Math.max(0, value.length() - 1)) + "…";
        }
        g.drawString(value, x, y);
    }

    private void polyline(Graphics2D g, List<String> points, Color color) {
        if (points.isEmpty()) {
            return;
        }
        Path2D path = new Path2D.Double();
        String[] first = points.get(0).split(",");
        path.moveTo(Double.parseDouble(first[0]), Double.parseDouble(first[1]));
        for (int i = 1; i < points.size(); i++) {
            String[] xy = points.get(i).split(",");
            path.lineTo(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]));
        }
        g.setColor(color);
        g.setStroke(new BasicStroke(2.8f));
        g.draw(path);
    }

    private String point(double x, double y) {
        return formatNumber(x, 1) + "," + formatNumber(y, 1);
    }

    private void frame(Graphics2D g, int left, int top, int width, int height) {
        g.setColor(Color.WHITE);
        g.fillRect(left, top, width, height);
        g.setColor(new Color(201, 208, 216));
        g.drawRect(left, top, width, height);
    }

    private void grid(Graphics2D g, int left, int top, int width, int height, int rows) {
        g.setColor(new Color(229, 232, 238));
        for (int i = 1; i <= rows; i++) {
            int y = top + height * i / (rows + 1);
            g.drawLine(left, y, left + width, y);
        }
    }

    private void axis(Graphics2D g, int left, int top, int width, int height, double zeroY, double[] ticks, boolean dualAxis) {
        g.setColor(new Color(55, 65, 81));
        g.drawLine(left, top, left, top + height);
        g.drawLine(left, top + height, left + width, top + height);
        g.drawLine(left, (int) zeroY, left + width, (int) zeroY);
        g.setFont(font(Font.PLAIN, 13));
        g.setColor(new Color(107, 114, 128));
        for (double tick : ticks) {
            double x = left + tick / 24.0 * width;
            g.drawLine((int) x, top, (int) x, top + height);
            g.drawString(String.valueOf((int) tick), (int) x - 4, top + height + 18);
        }
        if (dualAxis) {
            g.drawString("100%", left + width + 4, top + 12);
            g.drawString("50%", left + width + 4, (int) zeroY + 4);
            g.drawString("0%", left + width + 4, top + height);
        }
    }

    private void legend(Graphics2D g, int x, int y, List<Legend> legends) {
        int currentX = x;
        g.setFont(font(Font.PLAIN, 15));
        for (Legend legend : legends) {
            g.setColor(legend.color());
            g.fillRect(currentX, y - 9, 28, 4);
            g.setColor(new Color(75, 85, 99));
            g.drawString(legend.label(), currentX + 34, y - 2);
            currentX += Math.max(120, legend.label().length() * 12 + 56);
        }
    }

    private Graphics2D prepare(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        return g;
    }

    private Font font(int style, int size) {
        return new Font(chooseFontFamily(), style, size);
    }

    private String chooseFontFamily() {
        String[] families = {"Microsoft YaHei", "Noto Sans CJK SC", "PingFang SC", "SimHei", "SansSerif"};
        for (String family : families) {
            if (isFontAvailable(family)) {
                return family;
            }
        }
        return "SansSerif";
    }

    private boolean isFontAvailable(String family) {
        for (String name : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT)) {
            if (name.equalsIgnoreCase(family)) {
                return true;
            }
        }
        return false;
    }

    private String resolveOriginalFilename(long taskId) {
        return "任务文件 #" + taskId;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "-" : time.format(TIME_FORMAT);
    }

    private String formatDouble(double value) {
        return TWO.format(value);
    }

    private String formatPercent(double value) {
        return TWO.format(value * 100.0) + "%";
    }

    private String formatNumber(double value, int digits) {
        return String.format(Locale.ROOT, "%1$." + digits + "f", value);
    }

    private double normalizeSoc(double value) {
        if (value > 1.0) {
            return Math.min(1.0, value / 100.0);
        }
        return Math.max(0.0, value);
    }

    private double niceCeil(double value) {
        if (value <= 0.0) {
            return 1.0;
        }
        double magnitude = Math.pow(10.0, Math.floor(Math.log10(value)));
        double normalized = value / magnitude;
        double nice = normalized <= 1.0 ? 1.0 : normalized <= 2.0 ? 2.0 : normalized <= 5.0 ? 5.0 : 10.0;
        return nice * magnitude;
    }

    private double max(List<Double> values) {
        double max = 0.0;
        for (Double value : values) {
            if (value != null && Double.isFinite(value)) {
                max = Math.max(max, value);
            }
        }
        return max;
    }

    private String summaryText(JsonNode node, String key, String fallback) {
        if (node == null) {
            return fallback;
        }
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? fallback : value.asText(fallback);
    }

    private String schemeLabel(String scheme) {
        if (scheme == null || scheme.isBlank()) {
            return "折中最优";
        }
        if ("balanced".equalsIgnoreCase(scheme)) {
            return "折中最优";
        }
        return scheme;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private record LineSeries(String label, Color color, List<Double> values) {
    }

    private record Metric(String label, String value, String note, Color accent) {
    }

    private record KV(String label, String value) {
    }

    private record Legend(String label, Color color) {
    }
}
