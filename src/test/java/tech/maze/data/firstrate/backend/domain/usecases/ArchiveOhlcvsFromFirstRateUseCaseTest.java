package tech.maze.data.firstrate.backend.domain.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ArchiveOhlcvsFromFirstRateUseCaseTest {
  @Test
  void parsesDailyBarsFromZip() throws Exception {
    final ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(bos)) {
      zos.putNextEntry(new ZipEntry("AAPL_1day.txt"));
      zos.write("""
          2024-01-02,100,110,90,105,1000
          2024-01-03,105,115,100,110,2000
          """.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }

    final List<ArchiveOhlcvsFromFirstRateUseCase.Bar> bars =
        ArchiveOhlcvsFromFirstRateUseCase.parseZipCsv(new ByteArrayInputStream(bos.toByteArray()));

    assertThat(bars).hasSize(2);
    assertThat(bars.get(0).open()).isEqualTo("100");
    assertThat(bars.get(0).close()).isEqualTo("105");
    assertThat(bars.get(1).volume()).isEqualTo("2000");
  }

  @Test
  void mapsTimeframeSecondsToBarPeriod() {
    assertThat(ArchiveOhlcvsFromFirstRateUseCase.mapBarPeriod(60)).isEqualTo("1min");
    assertThat(ArchiveOhlcvsFromFirstRateUseCase.mapBarPeriod(300)).isEqualTo("5min");
    assertThat(ArchiveOhlcvsFromFirstRateUseCase.mapBarPeriod(86400)).isEqualTo("1day");
  }
}
