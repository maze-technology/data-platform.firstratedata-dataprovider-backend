package tech.maze.data.firstrate.backend.domain.ports.out;

import java.io.InputStream;
import java.util.List;
import tech.maze.data.firstrate.backend.domain.models.FirstRateTicker;

/**
 * Outbound port to the First Rate Data HTTP API.
 */
public interface FirstRatePort {
  /**
   * Lists tickers for a First Rate instrument type.
   *
   * @param listingType stock, etf, crypto, futures, index, fx, options
   * @return ticker rows
   */
  List<FirstRateTicker> listTickers(String listingType);

  /**
   * Downloads a historical data archive zip for one ticker.
   *
   * @param listingType instrument type
   * @param ticker symbol
   * @param timeframe bar period (e.g. 1min, 1day)
   * @param period archive window (full, day, week, month)
   * @return zip input stream (caller closes)
   */
  InputStream downloadDataFile(
      String listingType,
      String ticker,
      String timeframe,
      String period
  );
}
