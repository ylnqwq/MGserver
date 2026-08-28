package com.mg.mgserver.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfileFileService {
    private static final String TEMPLATE = """
            hour,buy_price,sell_price,load_kw,pv_kw,wt_kw
            0,0.42,0.25,92,0,66
            1,0.42,0.25,88,0,64
            2,0.42,0.25,80,0,68
            3,0.42,0.25,76,0,70
            4,0.42,0.25,78,0,72
            5,0.42,0.25,90,8,76
            6,0.52,0.32,108,18,74
            7,0.52,0.32,126,42,70
            8,0.52,0.32,136,72,66
            9,0.60,0.38,148,98,62
            10,0.60,0.38,142,112,58
            11,0.60,0.38,138,120,54
            12,0.60,0.38,134,118,50
            13,0.60,0.38,140,106,48
            14,0.60,0.38,148,84,46
            15,0.52,0.32,160,56,44
            16,0.52,0.32,176,28,48
            17,0.60,0.38,196,10,56
            18,0.60,0.38,205,0,62
            19,0.60,0.38,198,0,70
            20,0.52,0.32,174,0,76
            21,0.52,0.32,150,0,78
            22,0.52,0.32,126,0,74
            23,0.42,0.25,104,0,70
            """;

    public byte[] templateBytes() {
        return TEMPLATE.getBytes(StandardCharsets.UTF_8);
    }

    public List<ProfilePoint> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "请上传 CSV 或 XLSX 文件");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        try (InputStream inputStream = file.getInputStream()) {
            return parse(inputStream, filename);
        } catch (IOException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "文件读取失败");
        }
    }

    public List<ProfilePoint> parse(Path path, String originalFilename) {
        if (path == null || !Files.exists(path)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "文件路径不存在");
        }
        try (InputStream inputStream = Files.newInputStream(path)) {
            String filename = originalFilename == null || originalFilename.isBlank()
                    ? path.getFileName().toString()
                    : originalFilename;
            return parse(inputStream, filename);
        } catch (IOException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "文件读取失败");
        }
    }

    private List<ProfilePoint> parse(InputStream inputStream, String filename) throws IOException {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return parseCsv(inputStream);
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xlsm") || lower.endsWith(".xls")) {
            return parseExcel(inputStream);
        }
        throw new AppException(HttpStatus.BAD_REQUEST, "仅支持 .csv、.xlsx、.xlsm、.xls 文件");
    }

    private List<ProfilePoint> parseCsv(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build();
        try (CSVParser parser = new CSVParser(reader, format)) {
            Map<String, String> headers = canonicalHeaders(parser.getHeaderMap().keySet().stream().toList());
            List<ProfilePoint> points = new ArrayList<>();
            int rowIndex = 0;
            for (CSVRecord record : parser) {
                points.add(readPoint(rowIndex++, name -> record.get(headers.get(name))));
            }
            return validate(points);
        }
    }

    private List<ProfilePoint> parseExcel(InputStream inputStream) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Excel 首行必须是表头");
            }
            DataFormatter formatter = new DataFormatter();
            List<String> rawHeaders = new ArrayList<>();
            for (Cell cell : headerRow) {
                rawHeaders.add(formatter.formatCellValue(cell));
            }
            Map<String, Integer> headers = canonicalHeaderIndexes(rawHeaders);
            List<ProfilePoint> points = new ArrayList<>();
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                int rowIndex = points.size();
                points.add(readPoint(rowIndex, name -> {
                    Cell cell = row.getCell(headers.get(name), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    return cell == null ? "" : formatter.formatCellValue(cell);
                }));
            }
            return validate(points);
        }
    }

    private ProfilePoint readPoint(int rowIndex, ValueProvider provider) {
        return new ProfilePoint(
                (int) parseNumber(provider.get("hour"), rowIndex),
                parseNumber(provider.get("buy_price"), rowIndex),
                parseNumber(provider.get("sell_price"), rowIndex),
                parseNumber(provider.get("load_kw"), rowIndex),
                parseNumber(provider.get("pv_kw"), rowIndex),
                parseNumber(provider.get("wt_kw"), rowIndex)
        );
    }

    private double parseNumber(String value, int rowIndex) {
        if (value == null || value.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "第" + (rowIndex + 2) + " 行存在空值");
        }
        try {
            return Double.parseDouble(value.trim().replace(",", ""));
        } catch (NumberFormatException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "第" + (rowIndex + 2) + " 行数值格式错误: " + value);
        }
    }

    private List<ProfilePoint> validate(List<ProfilePoint> points) {
        if (points.size() != 24) {
            throw new AppException(HttpStatus.BAD_REQUEST, "当前版本模板需要 24 行小时数据");
        }
        for (int i = 0; i < points.size(); i++) {
            ProfilePoint point = points.get(i);
            if (point.hour() != i) {
                throw new AppException(HttpStatus.BAD_REQUEST, "hour 列必须从 0 到 23 顺序填写");
            }
            if (point.buyPrice() < 0 || point.sellPrice() < 0 || point.loadKw() < 0 || point.pvKw() < 0 || point.wtKw() < 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "价格、负荷、风电、光伏不能为负数");
            }
        }
        return points;
    }

    private Map<String, String> canonicalHeaders(List<String> rawHeaders) {
        Map<String, String> resolved = new HashMap<>();
        for (String rawHeader : rawHeaders) {
            resolved.put(canonicalName(rawHeader), rawHeader);
        }
        requireColumns(resolved);
        return resolved;
    }

    private Map<String, Integer> canonicalHeaderIndexes(List<String> rawHeaders) {
        Map<String, Integer> resolved = new HashMap<>();
        for (int i = 0; i < rawHeaders.size(); i++) {
            resolved.put(canonicalName(rawHeaders.get(i)), i);
        }
        requireColumns(resolved);
        return resolved;
    }

    private void requireColumns(Map<String, ?> headers) {
        List<String> required = List.of("hour", "buy_price", "sell_price", "load_kw", "pv_kw", "wt_kw");
        for (String column : required) {
            if (!headers.containsKey(column)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "缺少必填列: " + column);
            }
        }
    }

    private String canonicalName(String raw) {
        String value = raw == null ? "" : raw.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
        value = value.replace(" ", "").replace("-", "_");
        return switch (value) {
            case "hour", "time", "h", "小时", "时刻" -> "hour";
            case "buy_price", "price", "grid_buy_price", "购电价", "购电电价", "电价" -> "buy_price";
            case "sell_price", "grid_sell_price", "售电价", "售电电价", "上网电价" -> "sell_price";
            case "load_kw", "load", "demand", "负荷", "用电负荷" -> "load_kw";
            case "pv_kw", "pv", "solar", "photovoltaic", "光伏", "光伏出力" -> "pv_kw";
            case "wt_kw", "wind_kw", "wind", "wind_turbine", "风电", "风机", "风电出力" -> "wt_kw";
            default -> value;
        };
    }

    @FunctionalInterface
    private interface ValueProvider {
        String get(String name);
    }
}
