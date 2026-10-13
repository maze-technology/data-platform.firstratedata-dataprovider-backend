# firstrate-dataprovider-backend

First Rate Data provider for maze.trading.

## Role

- Consume `assets.fetch.request` / `markets.fetch.request` and publish one
  `Fetch*Response` per ticker into the configured response topics (used by
  assets-backend / markets-backend Sync fan-in).
- Consume `ohlcvs.fetch.request` and archive OHLCV bars into
  `ohlcvdatasets.archive.chunk` for ohlcvdatasets-backend to persist/seal.

## Configuration

| Env | Purpose | Default |
|-----|---------|---------|
| `FIRSTRATE_USERID` | First Rate API userid | _(required)_ |
| `FIRSTRATE_PROVIDER_KEY` | Fan-in provider string | `firstrate` |
| `FIRSTRATE_PROVIDER_ID` | UUID for metadata upserts | `aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee` |
| `FIRSTRATE_LISTING_TYPES` | ticker_listing types | `stock,etf,crypto` |
| `ASSETS_FETCH_REQUEST_TOPIC` | inbound | `assets.fetch.request` |
| `ASSETS_FETCH_RESPONSE_TOPIC` | outbound | `assets.fetch.response` |
| `MARKETS_FETCH_REQUEST_TOPIC` | inbound | `markets.fetch.request` |
| `MARKETS_FETCH_RESPONSE_TOPIC` | outbound | `markets.fetch.response` |
| `OHLCVS_FETCH_REQUEST_TOPIC` | inbound archive requests | `ohlcvs.fetch.request` |
| `OHLCVDATASETS_ARCHIVE_CHUNK_TOPIC` | outbound archive chunks | `ohlcvdatasets.archive.chunk` |
| `KAFKA_BROKERS` | Kafka | `localhost:9092` |

Register `firstrate` in assets/markets `maze.sync.expected-data-providers` when
running multi-provider sync.

## Development

```bash
export FIRSTRATE_USERID=...
./gradlew test
```
