package tech.maze.data.firstrate.backend.infrastructure.firstrate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.maze.data.firstrate.backend.domain.models.FirstRateTicker;

class FirstRateHttpClientTest {
  @Test
  void parsesTickerListingCsv() {
    final String csv = """
        ticker,name,startDate,endDate
        AAPL,Apple Inc,2000-01-01,
        BTC,Bitcoin,2015-01-01,2026-01-01
        """;
    final List<FirstRateTicker> tickers = FirstRateHttpClient.parseTickerListing(csv, "stock");
    assertThat(tickers).hasSize(2);
    assertThat(tickers.get(0).ticker()).isEqualTo("AAPL");
    assertThat(tickers.get(0).name()).isEqualTo("Apple Inc");
    assertThat(tickers.get(0).listingType()).isEqualTo("stock");
    assertThat(tickers.get(1).ticker()).isEqualTo("BTC");
  }
}
