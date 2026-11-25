package com.joedayz.springai.m08;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import com.joedayz.springai.m08.ClaimService.DamageReport;
import com.joedayz.springai.m08.ClaimService.DamageType;
import com.joedayz.springai.m08.ClaimService.ProductCategory;

class ClaimRulesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

    private static String status(ProductCategory cat, DamageType damage, boolean instructions, LocalDate bought) {
        var report = new DamageReport("x", cat, damage, "evidencia", instructions);
        return ClaimService.decide(report, "nota", bought, TODAY).status();
    }

    @Test
    void defectoDeFabricacionDentroDeGarantiaSeAprueba() {
        assertThat(status(ProductCategory.ELECTRONIC, DamageType.MANUFACTURING_DEFECT, false, TODAY.minusMonths(11)))
                .isEqualTo("APPROVED");
    }

    @Test
    void electronicoConMasDe12MesesSeRechaza() {
        assertThat(status(ProductCategory.ELECTRONIC, DamageType.MANUFACTURING_DEFECT, false, TODAY.minusMonths(13)))
                .isEqualTo("REJECTED");
    }

    @Test
    void accesorioTieneSolo3Meses() {
        assertThat(status(ProductCategory.ACCESSORY, DamageType.MANUFACTURING_DEFECT, false, TODAY.minusMonths(4)))
                .isEqualTo("REJECTED");
        assertThat(status(ProductCategory.ACCESSORY, DamageType.MANUFACTURING_DEFECT, false, TODAY.minusMonths(2)))
                .isEqualTo("APPROVED");
    }

    @Test
    void golpesYLiquidosNoSeCubren() {
        assertThat(status(ProductCategory.ELECTRONIC, DamageType.PHYSICAL_DAMAGE, false, TODAY.minusMonths(1))).isEqualTo("REJECTED");
        assertThat(status(ProductCategory.ELECTRONIC, DamageType.LIQUID_DAMAGE, false, TODAY.minusMonths(1))).isEqualTo("REJECTED");
    }

    @Test
    void fotoDudosaVaARevisionHumana() {
        assertThat(status(ProductCategory.ELECTRONIC, DamageType.UNCLEAR, false, TODAY.minusMonths(1))).isEqualTo("MANUAL_REVIEW");
    }

    @Test
    void instruccionesEnLaImagenNuncaSeApruebanAutomaticamente() {
        assertThat(status(ProductCategory.ELECTRONIC, DamageType.MANUFACTURING_DEFECT, true, TODAY.minusMonths(1)))
                .isEqualTo("MANUAL_REVIEW");
    }

    @Test
    void fechaDeCompraFuturaSeRechaza() {
        assertThat(status(ProductCategory.ELECTRONIC, DamageType.MANUFACTURING_DEFECT, false, TODAY.plusDays(1)))
                .isEqualTo("REJECTED");
    }

    @Test
    void detectaElFormatoPorLosBytesNoPorElNombre() {
        assertThat(Uploads.detectImage(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0})).isEqualTo("image/png");
        assertThat(Uploads.detectImage(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isEqualTo("image/jpeg");
        assertThat(Uploads.detectImage("<script>alert(1)</script>".getBytes())).isNull();
    }
}
