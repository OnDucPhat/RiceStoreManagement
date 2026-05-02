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
                    "Gao Tai Nguyen",
                    "Com no xop, mem, thom nhe, rao com; hop khach thich com kho, com chien hoac an voi canh.",
                    "20000",
                    "3000"),
            new SeedRiceProduct(
                    "Gao ST25",
                    "Hat dai, trang trong, thom nhe kieu la dua/com non; com deo mem, ngot, de nguoi van ngon.",
                    "25000",
                    "5000"),
            new SeedRiceProduct(
                    "Gao ST24",
                    "Hat thon dai, trang trong, thom la dua; com mem deo, ngot nhe, hop bua com gia dinh.",
                    "20000",
                    "2000"),
            new SeedRiceProduct(
                    "Gao Nang Hoa Go Cong",
                    "Hat hoi to va dai, thom nhe, com deo mem, ngot com; hop khach thich gao thom mien Tay.",
                    "17500",
                    "2000"),
            new SeedRiceProduct(
                    "Gao Thai Sua",
                    "Hat trang duc, thon dai, thom diu; com mem deo, ngot nhe, de nguoi van mem.",
                    "20000",
                    "2000"),
            new SeedRiceProduct(
                    "Gao Thom Lai",
                    "Deo mem, thom nhe, vi ngot thanh; de an hang ngay, hop khach thich com thom nhung khong qua dinh.",
                    "16000",
                    "2000"),
            new SeedRiceProduct(
                    "Gao Thom Thai",
                    "Hat trang deu, thom tu nhien; com mem deo, vi ngot nhe, hop khach thich gao deo de an.",
                    "16000",
                    "2000"),
            new SeedRiceProduct(
                    "Gao No",
                    "Com no to, toi xop, mem va it dinh; hop quan com, com chien hoac khach thich com rao.",
                    "13000",
                    "2000"),
            new SeedRiceProduct(
                    "Gao Sa Ri",
                    "Hat nho, hoi duc, kho tay; com no xop, mem, toi hat, hop com chien, banh xeo, bun va bep an.",
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
