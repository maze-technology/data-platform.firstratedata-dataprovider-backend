package tech.maze.data.firstrate.backend.domain.models;

/**
 * One ticker from First Rate {@code ticker_listing}.
 *
 * @param ticker symbol
 * @param name display name
 * @param startDate listing start (yyyy-MM-dd) or blank
 * @param endDate listing end (yyyy-MM-dd) or blank
 * @param listingType First Rate type (stock, etf, crypto, ...)
 */
public record FirstRateTicker(
    String ticker,
    String name,
    String startDate,
    String endDate,
    String listingType
) {
}
