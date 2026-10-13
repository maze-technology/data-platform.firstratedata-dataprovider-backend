package tech.maze.data.firstrate.backend.api.eventstream;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Value;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.maze.commons.eventstream.EventSender;
import tech.maze.commons.eventstream.MazeEventProperties;
import tech.maze.data.firstrate.backend.AppProperties;
import tech.maze.data.firstrate.backend.domain.usecases.ArchiveOhlcvsFromFirstRateUseCase;
import tech.maze.data.firstrate.backend.domain.usecases.FetchAssetsFromFirstRateUseCase;
import tech.maze.data.firstrate.backend.domain.usecases.FetchMarketsFromFirstRateUseCase;

/**
 * Kafka consumers for First Rate Data provider (assets/markets fetch + OHLCV archive).
 */
@Configuration
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@EnableConfigurationProperties({MazeEventProperties.class, AppProperties.class})
@Slf4j
public class FirstRateEventStreamConfiguration {
  EventSender eventSender;
  FetchAssetsFromFirstRateUseCase fetchAssetsFromFirstRateUseCase;
  FetchMarketsFromFirstRateUseCase fetchMarketsFromFirstRateUseCase;
  ArchiveOhlcvsFromFirstRateUseCase archiveOhlcvsFromFirstRateUseCase;
  AppProperties appProperties;

  /**
   * Handles FetchAssetsRequest fan-out from assets-backend.
   */
  @Bean
  public Consumer<CloudEvent> fetchAssetsRequestConsumer() {
    return event -> {
      if (!tech.maze.dtos.assets.events.EventTypes.FETCH_ASSETS_REQUEST.equals(event.getType())) {
        log.warn("Skipping event type {} (expected FetchAssetsRequest)", event.getType());
        return;
      }
      final String correlationId = resolveCorrelationId(event);
      final String replyTo = Optional.ofNullable(eventSender.resolveReplyTo(event))
          .filter(topic -> !topic.isBlank())
          .orElse(appProperties.getTopics().getAssetsFetchResponse());
      fetchAssetsFromFirstRateUseCase.fetch(correlationId, replyTo);
    };
  }

  /**
   * Handles FetchMarketsRequest fan-out from markets-backend.
   */
  @Bean
  public Consumer<CloudEvent> fetchMarketsRequestConsumer() {
    return event -> {
      if (!tech.maze.dtos.markets.events.EventTypes.FETCH_MARKETS_REQUEST.equals(event.getType())) {
        log.warn("Skipping event type {} (expected FetchMarketsRequest)", event.getType());
        return;
      }
      final String correlationId = resolveCorrelationId(event);
      final String replyTo = Optional.ofNullable(eventSender.resolveReplyTo(event))
          .filter(topic -> !topic.isBlank())
          .orElse(appProperties.getTopics().getMarketsFetchResponse());
      fetchMarketsFromFirstRateUseCase.fetch(correlationId, replyTo);
    };
  }

  /**
   * Handles OHLCV archive/fetch requests from ohlcvdatasets-backend.
   *
   * <p>Payload is a JSON string Value:
   * {@code {"listingType","ticker","quote","timeframeSeconds"}}
   * until a typed FetchMarketOHLCVsRequest is published.
   */
  @Bean
  public Consumer<CloudEvent> fetchOhlcvsRequestConsumer() {
    return event -> {
      if (!"tech.maze.ohlcvs.fetch.request".equals(event.getType())) {
        log.warn("Skipping event type {} (expected ohlcvs.fetch.request)", event.getType());
        return;
      }
      final String correlationId = resolveCorrelationId(event);
      final OhlcvFetchRequest request = parseOhlcvFetchRequest(event);
      archiveOhlcvsFromFirstRateUseCase.archive(
          correlationId,
          request.listingType(),
          request.ticker(),
          request.quote(),
          request.timeframeSeconds()
      );
    };
  }

  private String resolveCorrelationId(CloudEvent event) {
    return Optional.ofNullable(eventSender.resolveCorrelationId(event))
        .filter(id -> !id.isBlank())
        .orElseGet(() -> event.getId() == null ? UUID.randomUUID().toString() : event.getId());
  }

  private OhlcvFetchRequest parseOhlcvFetchRequest(CloudEvent event) {
    final CloudEventData data = event.getData();
    if (data == null) {
      throw new IllegalArgumentException("OHLCV fetch payload is required");
    }
    try {
      final Value value = Value.parseFrom(data.toBytes());
      if (!value.hasStringValue()) {
        throw new IllegalArgumentException("OHLCV fetch payload must be a JSON string Value");
      }
      return OhlcvFetchRequest.fromJson(value.getStringValue());
    } catch (InvalidProtocolBufferException ex) {
      // Allow raw JSON bytes for local tests
      return OhlcvFetchRequest.fromJson(new String(data.toBytes(), StandardCharsets.UTF_8));
    }
  }

  /**
   * Minimal fetch request for First Rate OHLCV archive.
   */
  record OhlcvFetchRequest(
      String listingType,
      String ticker,
      String quote,
      long timeframeSeconds
  ) {
    static OhlcvFetchRequest fromJson(String json) {
      // Tiny parser for the known keys (avoid full JSON dep surface in this path).
      final String listingType = extract(json, "listingType").orElse("stock");
      final String ticker = extract(json, "ticker")
          .orElseThrow(() -> new IllegalArgumentException("ticker is required"));
      final String quote = extract(json, "quote").orElse("USD");
      final long timeframeSeconds = Long.parseLong(
          extract(json, "timeframeSeconds").orElse("86400")
      );
      return new OhlcvFetchRequest(listingType, ticker, quote, timeframeSeconds);
    }

    private static Optional<String> extract(String json, String key) {
      final String needle = "\"" + key + "\"";
      final int keyIdx = json.indexOf(needle);
      if (keyIdx < 0) {
        return Optional.empty();
      }
      final int colon = json.indexOf(':', keyIdx + needle.length());
      if (colon < 0) {
        return Optional.empty();
      }
      int i = colon + 1;
      while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
        i++;
      }
      if (i >= json.length()) {
        return Optional.empty();
      }
      if (json.charAt(i) == '"') {
        final int end = json.indexOf('"', i + 1);
        if (end < 0) {
          return Optional.empty();
        }
        return Optional.of(json.substring(i + 1, end));
      }
      int end = i;
      while (end < json.length()
          && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
        end++;
      }
      return Optional.of(json.substring(i, end));
    }
  }
}
