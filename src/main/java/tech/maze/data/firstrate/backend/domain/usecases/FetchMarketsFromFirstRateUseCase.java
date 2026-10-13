package tech.maze.data.firstrate.backend.domain.usecases;

import com.google.protobuf.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.maze.commons.eventstream.EventSender;
import tech.maze.data.firstrate.backend.AppProperties;
import tech.maze.data.firstrate.backend.domain.models.FirstRateTicker;
import tech.maze.data.firstrate.backend.domain.ports.out.FirstRatePort;
import tech.maze.dtos.markets.enums.Type;
import tech.maze.dtos.markets.events.EventTypes;
import tech.maze.dtos.markets.models.MarketMetaDatas;
import tech.maze.dtos.markets.models.MarketMetaDatasMarket;
import tech.maze.dtos.markets.payloads.FetchMarketsRequest;
import tech.maze.dtos.markets.payloads.FetchMarketsResponse;

/**
 * Fans First Rate ticker listings into FetchMarketsResponse events (one market per event).
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
public class FetchMarketsFromFirstRateUseCase {
  FirstRatePort firstRatePort;
  EventSender eventSender;
  AppProperties appProperties;

  /**
   * Lists configured First Rate types and publishes market metadata responses.
   *
   * @param correlationId correlation id from the sync/fetch request
   * @param replyTo response topic (defaults to configured markets fetch response)
   */
  public void fetch(String correlationId, String replyTo) {
    final String destination = resolveDestination(replyTo);
    final String providerKey = appProperties.getProvider().getKey();
    final String providerId = appProperties.getProvider().getId().toString();
    final List<FirstRateTicker> tickers = loadAllTickers();
    long skipped = 0;

    for (int i = 0; i < tickers.size(); i++) {
      final FirstRateTicker ticker = tickers.get(i);
      final boolean last = i == tickers.size() - 1;
      final FetchMarketsResponse response = FetchMarketsResponse.newBuilder()
          .setSourceRequest(FetchMarketsRequest.getDefaultInstance())
          .setDataProvider(Value.newBuilder().setStringValue(providerKey).build())
          .setMarketMetaDatas(toMarketMetaDatas(ticker, providerId))
          .setIsLast(last)
          .setSkipped(last ? skipped : 0L)
          .build();

      final boolean sent = eventSender.send(
          destination,
          response,
          EventTypes.FETCH_MARKETS_RESPONSE,
          correlationExtensions(correlationId)
      );
      if (!sent) {
        skipped++;
        log.error("Failed to publish FetchMarketsResponse for {}", ticker.ticker());
      }
    }

    if (tickers.isEmpty()) {
      final FetchMarketsResponse response = FetchMarketsResponse.newBuilder()
          .setSourceRequest(FetchMarketsRequest.getDefaultInstance())
          .setDataProvider(Value.newBuilder().setStringValue(providerKey).build())
          .setIsLast(true)
          .setSkipped(skipped)
          .build();
      eventSender.send(
          destination,
          response,
          EventTypes.FETCH_MARKETS_RESPONSE,
          correlationExtensions(correlationId)
      );
    }
  }

  private List<FirstRateTicker> loadAllTickers() {
    final List<FirstRateTicker> all = new ArrayList<>();
    for (String listingType : appProperties.getFirstrate().getListingTypes()) {
      try {
        all.addAll(firstRatePort.listTickers(listingType));
      } catch (RuntimeException ex) {
        log.error("Failed to list First Rate tickers for type {}", listingType, ex);
      }
    }
    return all;
  }

  private MarketMetaDatas toMarketMetaDatas(FirstRateTicker ticker, String providerId) {
    final String quote = quoteFor(ticker.listingType());
    return MarketMetaDatas.newBuilder()
        .setDataProviderId(Value.newBuilder().setStringValue(providerId).build())
        .setMarket(
            MarketMetaDatasMarket.newBuilder()
                .setType(Type.SPOT)
                .setExchangeId(Value.newBuilder().setStringValue("firstrate").build())
                .setBaseId(Value.newBuilder().setStringValue(ticker.ticker()).build())
                .setQuoteId(Value.newBuilder().setStringValue(quote).build())
                .build()
        )
        .putExtraDatas("firstrate.listingType", ticker.listingType())
        .putExtraDatas("firstrate.name", nullToEmpty(ticker.name()))
        .putToolBox("provider", appProperties.getProvider().getKey())
        .build();
  }

  static String quoteFor(String listingType) {
    if (listingType != null && listingType.equalsIgnoreCase("fx")) {
      return "USD";
    }
    return "USD";
  }

  private String resolveDestination(String replyTo) {
    if (replyTo != null && !replyTo.isBlank()) {
      return replyTo;
    }
    return appProperties.getTopics().getMarketsFetchResponse();
  }

  private Map<String, String> correlationExtensions(String correlationId) {
    if (correlationId == null || correlationId.isBlank()) {
      return Map.of();
    }
    return Map.of(EventSender.CORRELATION_ID_EXTENSION, correlationId);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
