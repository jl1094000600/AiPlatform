package com.aipal.agent.marketing.service.tools;

import com.aipal.agent.marketing.entity.MarketingSalesData;
import com.aipal.agent.marketing.mapper.MarketingSalesDataMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalesQueryTool {

    private final MarketingSalesDataMapper salesDataMapper;

    public record TimeDimension(String type, LocalDate startDate, LocalDate endDate) {}

    public record SalesQueryRequest(
        String timeType,
        String region,
        String productCode,
        LocalDate startDate,
        LocalDate endDate
    ) {}

    public record SalesQueryResult(
        String timeType,
        String region,
        String productCode,
        List<SalesDataItem> data,
        BigDecimal totalAmount,
        Integer totalQuantity
    ) {}

    public record SalesDataItem(
        String period,
        String region,
        String product,
        BigDecimal salesAmount,
        Integer salesQuantity,
        BigDecimal profit
    ) {}

    public SalesQueryResult querySalesData(SalesQueryRequest request) {
        log.info("Querying sales data: timeType={}, region={}, product={}, startDate={}, endDate={}",
                request.timeType(), request.region(), request.productCode(), request.startDate(), request.endDate());

        LambdaQueryWrapper<MarketingSalesData> wrapper = new LambdaQueryWrapper<>();

        if (request.startDate() != null && request.endDate() != null) {
            wrapper.between(MarketingSalesData::getSalesDate, request.startDate(), request.endDate());
        }

        if (request.region() != null && !request.region().isEmpty() && !"全部".equals(request.region())) {
            wrapper.eq(MarketingSalesData::getRegion, request.region());
        }

        if (request.productCode() != null && !request.productCode().isEmpty() && !"全部".equals(request.productCode())) {
            wrapper.eq(MarketingSalesData::getProductCode, request.productCode());
        }

        List<MarketingSalesData> salesDataList = salesDataMapper.selectList(wrapper);

        List<SalesDataItem> dataItems = salesDataList.stream()
                .map(data -> new SalesDataItem(
                        formatPeriod(data.getSalesDate(), request.timeType()),
                        data.getRegion(),
                        data.getProductName(),
                        data.getSalesAmount(),
                        data.getSalesQuantity(),
                        data.getProfitAmount()
                ))
                .collect(Collectors.toList());

        BigDecimal totalAmount = dataItems.stream()
                .map(SalesDataItem::salesAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Integer totalQuantity = dataItems.stream()
                .mapToInt(SalesDataItem::salesQuantity)
                .sum();

        return new SalesQueryResult(
                request.timeType(),
                request.region(),
                request.productCode(),
                dataItems,
                totalAmount,
                totalQuantity
        );
    }

    private String formatPeriod(LocalDate date, String timeType) {
        if (date == null) return "";
        return switch (timeType.toLowerCase()) {
            case "日" -> date.toString();
            case "周" -> date.getYear() + "-W" + date.get(java.time.temporal.WeekFields.ISO.weekOfYear());
            case "月" -> date.getYear() + "-" + String.format("%02d", date.getMonthValue());
            case "季" -> date.getYear() + "-Q" + ((date.getMonthValue() - 1) / 3 + 1);
            case "年" -> String.valueOf(date.getYear());
            default -> date.toString();
        };
    }

    public List<String> getAvailableRegions() {
        return List.of("华东", "华南", "华北", "华西", "华中");
    }

    public List<String> getAvailableProducts() {
        LambdaQueryWrapper<MarketingSalesData> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(MarketingSalesData::getProductCode, MarketingSalesData::getProductName)
               .groupBy(MarketingSalesData::getProductCode, MarketingSalesData::getProductName);
        return salesDataMapper.selectList(wrapper).stream()
                .map(d -> d.getProductCode() + ":" + d.getProductName())
                .collect(Collectors.toList());
    }
}
