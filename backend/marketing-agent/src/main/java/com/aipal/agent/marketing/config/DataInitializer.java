package com.aipal.agent.marketing.config;

import com.aipal.agent.marketing.entity.MarketingSalesData;
import com.aipal.agent.marketing.mapper.MarketingSalesDataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final MarketingSalesDataMapper salesDataMapper;

    private static final List<String> REGIONS = List.of("华东", "华南", "华北", "华西", "华中");
    private static final List<String[]> PRODUCTS = List.of(
            new String[]{"P001", "智能手机"},
            new String[]{"P002", "笔记本电脑"},
            new String[]{"P003", "平板电脑"},
            new String[]{"P004", "智能手表"},
            new String[]{"P005", "无线耳机"}
    );

    @Override
    public void run(String... args) {
        if (salesDataMapper.selectCount(null) > 0) {
            log.info("Marketing sales data already exists, skipping initialization");
            return;
        }

        log.info("Initializing marketing sales data...");
        List<MarketingSalesData> dataList = generateSampleData();
        
        for (MarketingSalesData data : dataList) {
            salesDataMapper.insert(data);
        }
        
        log.info("Initialized {} marketing sales data records", dataList.size());
    }

    private List<MarketingSalesData> generateSampleData() {
        List<MarketingSalesData> dataList = new ArrayList<>();
        LocalDate startDate = LocalDate.now().minusYears(1);
        LocalDate endDate = LocalDate.now();
        Random random = new Random(42);

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            for (String region : REGIONS) {
                for (String[] product : PRODUCTS) {
                    MarketingSalesData data = new MarketingSalesData();
                    data.setSalesDate(date);
                    data.setRegion(region);
                    data.setProductCode(product[0]);
                    data.setProductName(product[1]);
                    
                    int baseQuantity = 50 + random.nextInt(150);
                    BigDecimal unitPrice = getUnitPrice(product[0]);
                    BigDecimal salesAmount = unitPrice.multiply(BigDecimal.valueOf(baseQuantity));
                    BigDecimal costRate = BigDecimal.valueOf(0.6 + random.nextDouble() * 0.15);
                    BigDecimal profitAmount = salesAmount.multiply(BigDecimal.ONE.subtract(costRate))
                            .setScale(2, RoundingMode.HALF_UP);
                    
                    data.setSalesAmount(salesAmount.setScale(2, RoundingMode.HALF_UP));
                    data.setSalesQuantity(baseQuantity);
                    data.setProfitAmount(profitAmount);
                    data.setCreateTime(LocalDateTime.now());
                    
                    dataList.add(data);
                }
            }
        }
        
        return dataList;
    }

    private BigDecimal getUnitPrice(String productCode) {
        return switch (productCode) {
            case "P001" -> new BigDecimal("2999.00");
            case "P002" -> new BigDecimal("5999.00");
            case "P003" -> new BigDecimal("1999.00");
            case "P004" -> new BigDecimal("1299.00");
            case "P005" -> new BigDecimal("799.00");
            default -> new BigDecimal("1999.00");
        };
    }
}
