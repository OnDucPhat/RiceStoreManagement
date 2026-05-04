package com.ricestoremanagement.config;

import com.ricestoremanagement.model.RiceProduct;
import com.ricestoremanagement.repository.RiceProductRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class RiceProductSeeder implements ApplicationRunner {
    private static final List<SeedRiceProduct> DEFAULT_PRODUCTS = List.of(
            new SeedRiceProduct(
                    "Gạo Tài Nguyên",
                    "Cơm nở xốp, mềm, thơm nhẹ, ráo cơm; hợp khách thích cơm khô, cơm chiên hoặc ăn với canh.",
                    "20000",
                    "3000"),
            new SeedRiceProduct(
                    "Gạo ST25",
                    "Hạt dài, trắng trong, thơm nhẹ kiểu lá dứa/cơm non; cơm dẻo mềm, ngọt, dễ người ăn ngon.",
                    "25000",
                    "5000"),
            new SeedRiceProduct(
                    "Gạo ST24",
                    "Hạt thon dài, trắng trong, thơm lá dứa; cơm mềm dẻo, ngọt nhẹ, hợp bữa cơm gia đình.",
                    "20000",
                    "2000"),
            new SeedRiceProduct(
                    "Gạo Nàng Hồng Gò Công",
                    "Hạt hơi to và dài, thơm nhẹ, cơm dẻo mềm, ngọt cơm; hợp khách thích gạo thơm miền Tây.",
                    "17500",
                    "2000"),
            new SeedRiceProduct(
                    "Gạo Thái Sữa",
                    "Hạt trắng đục, thon dài, thơm dịu; cơm mềm dẻo, ngọt nhẹ, dễ người ăn mềm.",
                    "20000",
                    "2000"),
            new SeedRiceProduct(
                    "Gạo Thơm Lại",
                    "Dẻo mềm, thơm nhẹ, vị ngọt thanh; dễ ăn hàng ngày, hợp khách thích cơm thơm nhưng không quá đặc.",
                    "16000",
                    "2000"),
            new SeedRiceProduct(
                    "Gạo Thơm Thái",
                    "Hạt trắng đều, thơm tự nhiên; cơm mềm dẻo, vị ngọt nhẹ, hợp khách thích gạo dẻo dễ ăn.",
                    "16000",
                    "2000"),
            new SeedRiceProduct(
                    "Gạo Nổ",
                    "Cơm nở tơi, xốp, mềm và ít dính; hợp quán cơm, cơm chiên hoặc khách thích cơm ráo.",
                    "13000",
                    "2000"),
            new SeedRiceProduct(
                    "Gạo Sa Ri",
                    "Hạt nhỏ, hơi dục, khô tay; cơm nở xốp, mềm, tơi hạt, hợp cơm chiên, bánh xèo, bún và bẹp ăn.",
                    "15000",
                    "2000"));

    private final RiceProductRepository riceProductRepository;
    private final boolean seedEnabled;

    public RiceProductSeeder(
            RiceProductRepository riceProductRepository,
            @Value("${app.seed.rice-products:true}") boolean seedEnabled) {
        this.riceProductRepository = riceProductRepository;
        this.seedEnabled = seedEnabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled || riceProductRepository.count() > 0) {
            return;
        }

        List<RiceProduct> products = DEFAULT_PRODUCTS.stream()
                .map(this::toProduct)
                .toList();
        riceProductRepository.saveAll(products);
    }

    private RiceProduct toProduct(SeedRiceProduct seed) {
        BigDecimal pricePerKg = new BigDecimal(seed.pricePerKg());
        BigDecimal profitPerKg = new BigDecimal(seed.profitPerKg());

        RiceProduct product = new RiceProduct();
        product.setName(seed.name());
        product.setCharacteristics(seed.characteristics());
        product.setPricePerKg(pricePerKg);
        product.setCostPerKg(pricePerKg.subtract(profitPerKg));
        product.setActive(true);
        return product;
    }

    private record SeedRiceProduct(
            String name,
            String characteristics,
            String pricePerKg,
            String profitPerKg) {
    }
}
