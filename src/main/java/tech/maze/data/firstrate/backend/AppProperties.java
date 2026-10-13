package tech.maze.data.firstrate.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the First Rate Data provider.
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@ConfigurationProperties(prefix = AppProperties.PREFIX)
public class AppProperties {
  public static final String PREFIX = "maze";

  String name = "firstrate-dataprovider";

  /**
   * Stable provider identity used in assets/markets upserts and fan-in.
   */
  Provider provider = new Provider();

  /**
   * First Rate HTTP API settings.
   */
  FirstRate firstrate = new FirstRate();

  /**
   * Kafka topic wiring for fetch responses / OHLCV archive.
   */
  Topics topics = new Topics();

  /**
   * Provider identity.
   */
  @Data
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class Provider {
    /**
     * String id used in Fetch*Response.dataProvider (fan-in key).
     */
    String key = "firstrate";

    /**
     * UUID stored on AssetMetaDatas / MarketMetaDatas.dataProviderId.
     */
    UUID id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
  }

  /**
   * First Rate API client properties.
   */
  @Data
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class FirstRate {
    String baseUrl = "https://firstratedata.com";

    /**
     * API userid from First Rate customer portal.
     */
    String userid = "";

    /**
     * Instrument types to sync for assets/markets (ticker_listing?type=...).
     */
    List<String> listingTypes = new ArrayList<>(List.of("stock", "etf", "crypto"));

    /**
     * Period for historical OHLCV archive downloads (full / day / week / month).
     */
    String archivePeriod = "full";

    /**
     * Bar timeframe period accepted by data_file (e.g. 1min, 5min, 1hour, 1day).
     */
    String barPeriod = "1day";
  }

  /**
   * Topic names (override via env).
   */
  @Data
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class Topics {
    String assetsFetchResponse = "assets.fetch.response";
    String marketsFetchResponse = "markets.fetch.response";
    String ohlcvsFetchRequest = "ohlcvs.fetch.request";
    String ohlcvdatasetsArchiveChunk = "ohlcvdatasets.archive.chunk";
  }
}
