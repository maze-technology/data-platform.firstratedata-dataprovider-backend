package tech.maze.data.firstrate.backend.domain.usecases;

import com.google.protobuf.Value;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.maze.commons.eventstream.EventSender;
import tech.maze.data.firstrate.backend.AppProperties;
import tech.maze.data.firstrate.backend.domain.ports.out.FirstRatePort;

/**
 * Downloads First Rate OHLCV archives and publishes archive chunks for ohlcvdatasets.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
public class ArchiveOhlcvsFromFirstRateUseCase {
  private static final int CHUNK_SIZE = 500;
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter MINUTE =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  FirstRatePort firstRatePort;
  EventSender eventSender;
  AppProperties appProperties;

  /**
   * Archives one market's OHLCV history into ohlcvdatasets via chunk events.
   *
   * @param correlationId correlation id
   * @param listingType First Rate type (stock, etf, crypto, ...)
   * @param ticker base symbol
   * @param quote quote symbol
   * @param timeframeSeconds bar size in seconds
   */
  public void archive(
      String correlationId,
      String listingType,
      String ticker,
      String quote,
      long timeframeSeconds
  ) {
    final String barPeriod = mapBarPeriod(timeframeSeconds);
    final String period = appProperties.getFirstrate().getArchivePeriod();
    final String destination = appProperties.getTopics().getOhlcvdatasetsArchiveChunk();
    final String providerKey = appProperties.getProvider().getKey();

    final List<Bar> bars;
    try (InputStream zipStream = firstRatePort.downloadDataFile(
        listingType,
        ticker,
        barPeriod,
        period
    )) {
      bars = parseZipCsv(zipStream);
    } catch (Exception ex) {
      throw new IllegalStateException(
          "Failed to download/parse First Rate archive for " + ticker,
          ex
      );
    }

    if (bars.isEmpty()) {
      publishChunk(
          destination,
          correlationId,
          providerKey,
          listingType,
          ticker,
          quote,
          timeframeSeconds,
          0,
          true,
          List.of()
      );
      return;
    }

    int position = 0;
    for (int i = 0; i < bars.size(); i += CHUNK_SIZE) {
      final int end = Math.min(i + CHUNK_SIZE, bars.size());
      final List<Bar> chunk = bars.subList(i, end);
      final boolean last = end >= bars.size();
      publishChunk(
          destination,
          correlationId,
          providerKey,
          listingType,
          ticker,
          quote,
          timeframeSeconds,
          position,
          last,
          chunk
      );
      position += chunk.size();
    }
  }

  private void publishChunk(
      String destination,
      String correlationId,
      String providerKey,
      String listingType,
      String ticker,
      String quote,
      long timeframeSeconds,
      int positionStart,
      boolean last,
      List<Bar> bars
  ) {
    final String json = toChunkJson(
        providerKey,
        listingType,
        ticker,
        quote,
        timeframeSeconds,
        positionStart,
        last,
        bars
    );
    final Value payload = Value.newBuilder().setStringValue(json).build();
    final boolean sent = eventSender.send(
        destination,
        payload,
        "tech.maze.ohlcvdatasets.archive.chunk",
        correlationExtensions(correlationId)
    );
    if (!sent) {
      throw new IllegalStateException(
          "Failed to publish archive chunk for " + ticker + " at " + positionStart
      );
    }
  }

  static String mapBarPeriod(long timeframeSeconds) {
    if (timeframeSeconds <= 60) {
      return "1min";
    }
    if (timeframeSeconds <= 300) {
      return "5min";
    }
    if (timeframeSeconds <= 1800) {
      return "30min";
    }
    if (timeframeSeconds <= 3600) {
      return "1hour";
    }
    return "1day";
  }

  static List<Bar> parseZipCsv(InputStream zipStream) throws Exception {
    final List<Bar> bars = new ArrayList<>();
    try (ZipInputStream zis = new ZipInputStream(zipStream)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory() || !entry.getName().endsWith(".txt")) {
          continue;
        }
        final BufferedReader reader = new BufferedReader(
            new InputStreamReader(zis, StandardCharsets.UTF_8)
        );
        String line;
        while ((line = reader.readLine()) != null) {
          final Bar bar = parseBarLine(line);
          if (bar != null) {
            bars.add(bar);
          }
        }
      }
    }
    return bars;
  }

  static Bar parseBarLine(String line) {
    final String trimmed = line.trim();
    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
      return null;
    }
    final String[] parts = trimmed.split(",", -1);
    if (parts.length < 6) {
      return null;
    }
    final Instant createdAt = parseTimestamp(parts[0].trim());
    if (createdAt == null) {
      return null;
    }
    return new Bar(
        parts[1].trim(),
        parts[2].trim(),
        parts[3].trim(),
        parts[4].trim(),
        parts[5].trim(),
        createdAt.toEpochMilli()
    );
  }

  static Instant parseTimestamp(String raw) {
    try {
      if (raw.length() <= 10) {
        return LocalDate.parse(raw, DAY).atStartOfDay().toInstant(ZoneOffset.UTC);
      }
      return LocalDateTime.parse(raw, MINUTE).toInstant(ZoneOffset.UTC);
    } catch (RuntimeException ex) {
      return null;
    }
  }

  static String toChunkJson(
      String dataProvider,
      String listingType,
      String base,
      String quote,
      long timeframeSeconds,
      int positionStart,
      boolean last,
      List<Bar> bars
  ) {
    final StringBuilder sb = new StringBuilder();
    sb.append('{')
        .append("\"dataProvider\":\"").append(escape(dataProvider)).append("\",")
        .append("\"listingType\":\"").append(escape(listingType)).append("\",")
        .append("\"exchange\":\"firstrate\",")
        .append("\"base\":\"").append(escape(base)).append("\",")
        .append("\"quote\":\"").append(escape(quote)).append("\",")
        .append("\"marketType\":\"SPOT\",")
        .append("\"timeframeSeconds\":").append(timeframeSeconds).append(',')
        .append("\"positionStart\":").append(positionStart).append(',')
        .append("\"isLast\":").append(last).append(',')
        .append("\"bars\":[");
    for (int i = 0; i < bars.size(); i++) {
      final Bar bar = bars.get(i);
      if (i > 0) {
        sb.append(',');
      }
      sb.append('{')
          .append("\"open\":\"").append(escape(bar.open())).append("\",")
          .append("\"high\":\"").append(escape(bar.high())).append("\",")
          .append("\"low\":\"").append(escape(bar.low())).append("\",")
          .append("\"close\":\"").append(escape(bar.close())).append("\",")
          .append("\"volume\":\"").append(escape(bar.volume())).append("\",")
          .append("\"createdAtEpochMs\":").append(bar.createdAtEpochMs())
          .append('}');
    }
    sb.append("]}");
    return sb.toString();
  }

  private Map<String, String> correlationExtensions(String correlationId) {
    if (correlationId == null || correlationId.isBlank()) {
      return Map.of();
    }
    return Map.of(EventSender.CORRELATION_ID_EXTENSION, correlationId);
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  /**
   * One OHLCV bar parsed from First Rate CSV.
   */
  record Bar(
      String open,
      String high,
      String low,
      String close,
      String volume,
      long createdAtEpochMs
  ) {
  }
}
