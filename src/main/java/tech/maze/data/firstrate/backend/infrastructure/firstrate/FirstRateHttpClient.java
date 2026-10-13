package tech.maze.data.firstrate.backend.infrastructure.firstrate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tech.maze.data.firstrate.backend.AppProperties;
import tech.maze.data.firstrate.backend.domain.models.FirstRateTicker;
import tech.maze.data.firstrate.backend.domain.ports.out.FirstRatePort;

/**
 * HTTP client for firstratedata.com API (ticker listing + data_file zip).
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
public class FirstRateHttpClient implements FirstRatePort {
  AppProperties appProperties;
  WebClient.Builder webClientBuilder;

  @Override
  public List<FirstRateTicker> listTickers(String listingType) {
    final String userid = requireUserid();
    final String body = client().get()
        .uri(uriBuilder -> uriBuilder
            .path("/api/ticker_listing")
            .queryParam("type", listingType)
            .queryParam("userid", userid)
            .build())
        .retrieve()
        .bodyToMono(String.class)
        .block();

    return parseTickerListing(body == null ? "" : body, listingType);
  }

  @Override
  public InputStream downloadDataFile(
      String listingType,
      String ticker,
      String timeframe,
      String period
  ) {
    final String userid = requireUserid();
    final byte[] bytes = client().get()
        .uri(uriBuilder -> uriBuilder
            .path("/api/data_file")
            .queryParam("type", listingType)
            .queryParam("period", period)
            .queryParam("timeframe", timeframe)
            .queryParam("ticker", ticker)
            .queryParam("userid", userid)
            .build())
        .retrieve()
        .bodyToMono(byte[].class)
        .block();

    if (bytes == null || bytes.length == 0) {
      throw new IllegalStateException(
          "Empty data_file response for " + listingType + "/" + ticker
      );
    }
    return new ByteArrayInputStream(bytes);
  }

  static List<FirstRateTicker> parseTickerListing(String csv, String listingType) {
    final List<FirstRateTicker> tickers = new ArrayList<>();
    for (String line : csv.split("\\R")) {
      final String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      final String[] parts = trimmed.split(",", -1);
      if (parts.length < 2) {
        continue;
      }
      final String ticker = parts[0].trim();
      final String name = parts[1].trim();
      if (ticker.isEmpty()) {
        continue;
      }
      // Skip header-like rows
      if ("ticker".equalsIgnoreCase(ticker)) {
        continue;
      }
      final String start = parts.length > 2 ? parts[2].trim() : "";
      final String end = parts.length > 3 ? parts[3].trim() : "";
      tickers.add(new FirstRateTicker(ticker, name, start, end, listingType));
    }
    return tickers;
  }

  private WebClient client() {
    return webClientBuilder
        .baseUrl(appProperties.getFirstrate().getBaseUrl())
        .build();
  }

  private String requireUserid() {
    final String userid = appProperties.getFirstrate().getUserid();
    if (userid == null || userid.isBlank()) {
      throw new IllegalStateException(
          "maze.firstrate.userid (FIRSTRATE_USERID) is required"
      );
    }
    return userid;
  }
}
