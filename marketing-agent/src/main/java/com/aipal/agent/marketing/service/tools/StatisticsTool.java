package com.aipal.agent.marketing.service.tools;

import com.aipal.agent.marketing.entity.MarketingSalesData;
import com.aipal.agent.marketing.mapper.MarketingSalesDataMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatisticsTool {

    private final MarketingSalesDataMapper salesDataMapper;

    public record StatisticsRequest(
        LocalDate startDate,
        LocalDate endDate,
        List<String> groupByDimensions
    ) {}

    public record StatisticsResult(
        SummaryData summary,
        List<RankingItem> rankings,
        ChartData chartData
    ) {}

    public record SummaryData(
        BigDecimal totalSalesAmount,
        Integer totalSalesQuantity,
        BigDecimal totalProfit,
        BigDecimal avgOrderAmount,
        BigDecimal profitRate
    ) {}

    public record RankingItem(
        String dimension,
        String dimensionValue,
        BigDecimal salesAmount,
        Integer salesQuantity,
        BigDecimal profit,
        Integer rank
    ) {}

    public record ChartData(
        List<String> categories,
        List<SeriesData> series
    ) {}

    public record SeriesData(
        String name,
        String type,
        List<BigDecimal> data
    ) {}

    public StatisticsResult generateStatistics(StatisticsRequest request) {
        log.info("Generating statistics: startDate={}, endDate={}, groupBy={}",
                request.startDate(), request.endDate(), request.groupByDimensions());

        LambdaQueryWrapper<MarketingSalesData> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(MarketingSalesData::getSalesDate, request.startDate(), request.endDate());
        List<MarketingSalesData> dataList = salesDataMapper.selectList(wrapper);

        SummaryData summary = calculateSummary(dataList);
        List<RankingItem> rankings = calculateRankings(dataList, request.groupByDimensions());
        ChartData chartData = generateChartData(dataList, request.groupByDimensions());

        return new StatisticsResult(summary, rankings, chartData);
    }

    private SummaryData calculateSummary(List<MarketingSalesData> dataList) {
        BigDecimal totalSalesAmount = dataList.stream()
                .map(MarketingSalesData::getSalesAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Integer totalQuantity = dataList.stream()
                .mapToInt(MarketingSalesData::getSalesQuantity)
                .sum();

        BigDecimal totalProfit = dataList.stream()
                .map(MarketingSalesData::getProfitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgOrderAmount = dataList.isEmpty() ? BigDecimal.ZERO
                : totalSalesAmount.divide(BigDecimal.valueOf(dataList.size()), 2, RoundingMode.HALF_UP);

        BigDecimal profitRate = totalSalesAmount.compareTo(BigDecimal.ZERO) > 0
                ? totalProfit.divide(totalSalesAmount, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return new SummaryData(totalSalesAmount, totalQuantity, totalProfit, avgOrderAmount, profitRate);
    }

    private List<RankingItem> calculateRankings(List<MarketingSalesData> dataList, List<String> dimensions) {
        List<RankingItem> rankings = new ArrayList<>();

        if (dimensions == null || dimensions.isEmpty()) {
            dimensions = List.of("region", "product");
        }

        for (String dimension : dimensions) {
            Map<String, List<MarketingSalesData>> grouped;
            if ("region".equals(dimension)) {
                grouped = dataList.stream().collect(Collectors.groupingBy(MarketingSalesData::getRegion));
            } else if ("product".equals(dimension)) {
                grouped = dataList.stream().collect(Collectors.groupingBy(MarketingSalesData::getProductName));
            } else {
                continue;
            }

            List<RankingItem> dimensionRankings = grouped.entrySet().stream()
                    .map(e -> {
                        BigDecimal amount = e.getValue().stream()
                                .map(MarketingSalesData::getSalesAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        Integer quantity = e.getValue().stream()
                                .mapToInt(MarketingSalesData::getSalesQuantity)
                                .sum();
                        BigDecimal profit = e.getValue().stream()
                                .map(MarketingSalesData::getProfitAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        return new RankingItem(dimension, e.getKey(), amount, quantity, profit, 0);
                    })
                    .sorted(Comparator.comparing(RankingItem::salesAmount).reversed())
                    .collect(Collectors.toList());

            for (int i = 0; i < dimensionRankings.size(); i++) {
                RankingItem item = dimensionRankings.get(i);
                dimensionRankings.set(i, new RankingItem(
                        item.dimension(), item.dimensionValue(), item.salesAmount(),
                        item.salesQuantity(), item.profit(), i + 1
                ));
            }

            rankings.addAll(dimensionRankings);
        }

        return rankings;
    }

    private ChartData generateChartData(List<MarketingSalesData> dataList, List<String> dimensions) {
        Map<String, List<MarketingSalesData>> grouped = dataList.stream()
                .collect(Collectors.groupingBy(d -> d.getSalesDate().toString()));

        List<String> categories = grouped.keySet().stream().sorted().collect(Collectors.toList());

        List<SeriesData> series = new ArrayList<>();

        BigDecimal amountSum = grouped.values().stream()
                .map(list -> list.stream().map(MarketingSalesData::getSalesAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .collect(Collectors.toList()).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        series.add(new SeriesData("销售额", "line",
                categories.stream().map(cat -> {
                    List<MarketingSalesData> dayData = grouped.get(cat);
                    return dayData.stream().map(MarketingSalesData::getSalesAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                }).collect(Collectors.toList())
        ));

        series.add(new SeriesData("销售量", "bar",
                categories.stream().map(cat -> {
                    List<MarketingSalesData> dayData = grouped.get(cat);
                    return BigDecimal.valueOf(dayData.stream().mapToInt(MarketingSalesData::getSalesQuantity).sum());
                }).collect(Collectors.toList())
        ));

        series.add(new SeriesData("利润", "line",
                categories.stream().map(cat -> {
                    List<MarketingSalesData> dayData = grouped.get(cat);
                    return dayData.stream().map(MarketingSalesData::getProfitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                }).collect(Collectors.toList())
        ));

        if (dimensions != null && dimensions.contains("region")) {
            Map<String, BigDecimal> regionAmounts = dataList.stream()
                    .collect(Collectors.groupingBy(MarketingSalesData::getRegion,
                            Collectors.reducing(BigDecimal.ZERO, MarketingSalesData::getSalesAmount, BigDecimal::add)));

            series.add(new SeriesData("区域销售占比", "pie",
                    new ArrayList<>(regionAmounts.values())
            ));
        }

        return new ChartData(categories, series);
    }

    public ChartData generateEChartsData(String chartType, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<MarketingSalesData> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(MarketingSalesData::getSalesDate, startDate, endDate);
        List<MarketingSalesData> dataList = salesDataMapper.selectList(wrapper);

        return generateChartData(dataList, List.of("region"));
    }

    public String exportData(String format, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<MarketingSalesData> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(MarketingSalesData::getSalesDate, startDate, endDate);
        List<MarketingSalesData> dataList = salesDataMapper.selectList(wrapper);

        if ("csv".equalsIgnoreCase(format)) {
            StringBuilder csv = new StringBuilder("日期,区域,产品编码,产品名称,销售额,销售数量,利润\n");
            for (MarketingSalesData data : dataList) {
                csv.append(data.getSalesDate()).append(",")
                   .append(data.getRegion()).append(",")
                   .append(data.getProductCode()).append(",")
                   .append(data.getProductName()).append(",")
                   .append(data.getSalesAmount()).append(",")
                   .append(data.getSalesQuantity()).append(",")
                   .append(data.getProfitAmount()).append("\n");
            }
            return csv.toString();
        }

        return "{\"error\": \"Unsupported format: " + format + "\"}";
    }
}
