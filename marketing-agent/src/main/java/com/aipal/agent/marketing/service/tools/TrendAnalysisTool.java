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
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrendAnalysisTool {

    private final MarketingSalesDataMapper salesDataMapper;

    public record TrendAnalysisRequest(
        String dimension,
        String compareType,
        LocalDate startDate,
        LocalDate endDate
    ) {}

    public record TrendAnalysisResult(
        String dimension,
        String compareType,
        List<TrendDataItem> currentPeriod,
        List<TrendDataItem> previousPeriod,
        List<ComparisonItem> comparisons
    ) {}

    public record TrendDataItem(
        String period,
        BigDecimal amount,
        Integer quantity
    ) {}

    public record ComparisonItem(
        String period,
        BigDecimal currentAmount,
        BigDecimal previousAmount,
        BigDecimal yoyGrowth,
        BigDecimal momGrowth,
        BigDecimal yoyGrowthRate,
        BigDecimal momGrowthRate
    ) {}

    public TrendAnalysisResult analyzeTrend(TrendAnalysisRequest request) {
        log.info("Analyzing trend: dimension={}, compareType={}, startDate={}, endDate={}",
                request.dimension(), request.compareType(), request.startDate(), request.endDate());

        LocalDate previousStart = request.startDate().minusDays(ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1);
        LocalDate previousEnd = request.startDate().minusDays(1);

        List<MarketingSalesData> currentData = fetchData(request.startDate(), request.endDate(), request.dimension());
        List<MarketingSalesData> previousData = fetchData(previousStart, previousEnd, request.dimension());

        List<TrendDataItem> currentItems = aggregateByPeriod(currentData, request.compareType());
        List<TrendDataItem> previousItems = aggregateByPeriod(previousData, request.compareType());

        List<ComparisonItem> comparisons = buildComparisons(currentItems, previousItems);

        return new TrendAnalysisResult(
                request.dimension(),
                request.compareType(),
                currentItems,
                previousItems,
                comparisons
        );
    }

    private List<MarketingSalesData> fetchData(LocalDate startDate, LocalDate endDate, String dimension) {
        LambdaQueryWrapper<MarketingSalesData> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(MarketingSalesData::getSalesDate, startDate, endDate);
        return salesDataMapper.selectList(wrapper);
    }

    private List<TrendDataItem> aggregateByPeriod(List<MarketingSalesData> data, String compareType) {
        Map<String, List<MarketingSalesData>> grouped = data.stream()
                .collect(Collectors.groupingBy(d -> getPeriodKey(d.getSalesDate(), compareType)));

        return grouped.entrySet().stream()
                .map(e -> new TrendDataItem(
                        e.getKey(),
                        e.getValue().stream().map(MarketingSalesData::getSalesAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                        e.getValue().stream().mapToInt(MarketingSalesData::getSalesQuantity).sum()
                ))
                .sorted(Comparator.comparing(TrendDataItem::period))
                .collect(Collectors.toList());
    }

    private String getPeriodKey(LocalDate date, String compareType) {
        if (date == null) return "";
        return switch (compareType.toLowerCase()) {
            case "日" -> date.toString();
            case "周" -> date.getYear() + "-W" + date.get(java.time.temporal.WeekFields.ISO.weekOfYear());
            case "月" -> date.getYear() + "-" + String.format("%02d", date.getMonthValue());
            case "季" -> date.getYear() + "-Q" + ((date.getMonthValue() - 1) / 3 + 1);
            case "年" -> String.valueOf(date.getYear());
            default -> date.toString();
        };
    }

    private List<ComparisonItem> buildComparisons(List<TrendDataItem> current, List<TrendDataItem> previous) {
        Map<String, TrendDataItem> previousMap = previous.stream()
                .collect(Collectors.toMap(TrendDataItem::period, item -> item));

        List<ComparisonItem> results = new ArrayList<>();
        for (int i = 0; i < current.size(); i++) {
            TrendDataItem currentItem = current.get(i);
            String prevPeriod = getPreviousPeriod(currentItem.period());
            TrendDataItem prevItem = previousMap.get(prevPeriod);

            BigDecimal prevAmount = prevItem != null ? prevItem.amount() : BigDecimal.ZERO;
            BigDecimal yoyGrowth = currentItem.amount().subtract(prevAmount);
            BigDecimal yoyGrowthRate = prevAmount.compareTo(BigDecimal.ZERO) > 0
                    ? yoyGrowth.divide(prevAmount, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            BigDecimal momGrowth = BigDecimal.ZERO;
            BigDecimal momGrowthRate = BigDecimal.ZERO;
            if (i > 0) {
                TrendDataItem prevCurrentItem = current.get(i - 1);
                momGrowth = currentItem.amount().subtract(prevCurrentItem.amount());
                momGrowthRate = prevCurrentItem.amount().compareTo(BigDecimal.ZERO) > 0
                        ? momGrowth.divide(prevCurrentItem.amount(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO;
            }

            results.add(new ComparisonItem(
                    currentItem.period(),
                    currentItem.amount(),
                    prevAmount,
                    yoyGrowth,
                    momGrowth,
                    yoyGrowthRate,
                    momGrowthRate
            ));
        }
        return results;
    }

    private String getPreviousPeriod(String currentPeriod) {
        if (currentPeriod == null || currentPeriod.isEmpty()) return "";
        if (currentPeriod.contains("-W")) {
            String[] parts = currentPeriod.split("-W");
            int week = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[0]);
            if (week == 1) {
                return (year - 1) + "-W52";
            }
            return year + "-W" + String.format("%02d", week - 1);
        }
        if (currentPeriod.matches("\\d{4}-\\d{2}")) {
            String[] parts = currentPeriod.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            if (month == 1) {
                return (year - 1) + "-12";
            }
            return year + "-" + String.format("%02d", month - 1);
        }
        if (currentPeriod.matches("\\d{4}-Q\\d")) {
            String[] parts = currentPeriod.split("-Q");
            int year = Integer.parseInt(parts[0]);
            int quarter = Integer.parseInt(parts[1]);
            if (quarter == 1) {
                return (year - 1) + "-Q4";
            }
            return year + "-Q" + (quarter - 1);
        }
        if (currentPeriod.matches("\\d{4}")) {
            return String.valueOf(Integer.parseInt(currentPeriod) - 1);
        }
        return currentPeriod;
    }
}
