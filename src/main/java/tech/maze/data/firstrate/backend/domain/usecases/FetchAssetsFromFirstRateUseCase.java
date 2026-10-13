package tech.maze.data.firstrate.backend.domain.usecases;

import com.google.protobuf.Value;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.maze.commons.eventstream.EventSender;
import tech.maze.data.firstrate.backend.AppProperties;
import tech.maze.data.firstrate.backend.domain.models.FirstRateTicker;
import tech.maze.data.firstrate.backend.domain.ports.out.FirstRatePort;
import tech.maze.dtos.assets.enums.PrimaryClass;
import tech.maze.dtos.assets.events.EventTypes;
import tech.maze.dtos.assets.models.AssetMetaDatas;
import tech.maze.dtos.assets.models.AssetMetaDatasAsset;
import tech.maze.dtos.assets.payloads.FetchAssetsRequest;
import tech.maze.dtos.assets.payloads.FetchAssetsResponse;

/**
 * Fans First Rate ticker listings into FetchAssetsResponse events (one asset per event).
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
public class FetchAssetsFromFirstRateUseCase {
  FirstRatePort firstRatePort;
  EventSender eventSender;
  AppProperties appProperties;

  /**
   * Lists configured First Rate types and publishes asset metadata responses.
   *
   * @param correlationId correlation id from the sync/fetch request
   * @param replyTo response topic (defaults to configured assets fetch response)
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
      final FetchAssetsResponse response = FetchAssetsResponse.newBuilder()
          .setSourceRequest(FetchAssetsRequest.getDefaultInstance())
          .setDataProvider(Value.newBuilder().setStringValue(providerKey).build())
          .setAssetMetaDatas(toAssetMetaDatas(ticker, providerId))
          .setIsLast(last)
          .setSkipped(last ? skipped : 0L)
          .build();

      final boolean sent = eventSender.send(
          destination,
          response,
          EventTypes.FETCH_ASSETS_RESPONSE,
          correlationExtensions(correlationId)
      );
      if (!sent) {
        skipped++;
        log.error("Failed to publish FetchAssetsResponse for {}", ticker.ticker());
      }
    }

    if (tickers.isEmpty()) {
      publishEmptyLast(destination, correlationId, providerKey, skipped);
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

  private AssetMetaDatas toAssetMetaDatas(FirstRateTicker ticker, String providerId) {
    final String name = ticker.name() == null || ticker.name().isBlank()
        ? ticker.ticker()
        : ticker.name();
    return AssetMetaDatas.newBuilder()
        .setDataProviderId(Value.newBuilder().setStringValue(providerId).build())
        .setAsset(
            AssetMetaDatasAsset.newBuilder()
                .setId(ticker.ticker())
                .setSymbol(ticker.ticker())
                .setName(name)
                .build()
        )
        .setPrimaryClass(mapPrimaryClass(ticker.listingType()))
        .putExtraDatas("firstrate.listingType", ticker.listingType())
        .putExtraDatas("firstrate.startDate", nullToEmpty(ticker.startDate()))
        .putExtraDatas("firstrate.endDate", nullToEmpty(ticker.endDate()))
        .putToolBox("provider", appProperties.getProvider().getKey())
        .build();
  }

  static PrimaryClass mapPrimaryClass(String listingType) {
    if (listingType == null) {
      return PrimaryClass.CRYPTO;
    }
    // EQUITY is not in assets PrimaryClass yet — stock/etf/index land as CRYPTO
    // with firstrate.listingType in extraDatas until assets-specs grows the enum.
    return switch (listingType.toLowerCase()) {
      case "fx" -> PrimaryClass.FIAT;
      default -> PrimaryClass.CRYPTO;
    };
  }

  private void publishEmptyLast(
      String destination,
      String correlationId,
      String providerKey,
      long skipped
  ) {
    final FetchAssetsResponse response = FetchAssetsResponse.newBuilder()
        .setSourceRequest(FetchAssetsRequest.getDefaultInstance())
        .setDataProvider(Value.newBuilder().setStringValue(providerKey).build())
        .setIsLast(true)
        .setSkipped(skipped)
        .build();
    eventSender.send(
        destination,
        response,
        EventTypes.FETCH_ASSETS_RESPONSE,
        correlationExtensions(correlationId)
    );
  }

  private String resolveDestination(String replyTo) {
    if (replyTo != null && !replyTo.isBlank()) {
      return replyTo;
    }
    return appProperties.getTopics().getAssetsFetchResponse();
  }

  private java.util.Map<String, String> correlationExtensions(String correlationId) {
    if (correlationId == null || correlationId.isBlank()) {
      return java.util.Map.of();
    }
    return java.util.Map.of(EventSender.CORRELATION_ID_EXTENSION, correlationId);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
