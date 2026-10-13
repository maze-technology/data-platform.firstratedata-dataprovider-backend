package tech.maze.data.firstrate.backend.domain.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.maze.commons.eventstream.EventSender;
import tech.maze.data.firstrate.backend.AppProperties;
import tech.maze.data.firstrate.backend.domain.models.FirstRateTicker;
import tech.maze.data.firstrate.backend.domain.ports.out.FirstRatePort;
import tech.maze.dtos.assets.enums.PrimaryClass;
import tech.maze.dtos.assets.events.EventTypes;
import tech.maze.dtos.assets.payloads.FetchAssetsResponse;

@ExtendWith(MockitoExtension.class)
class FetchAssetsFromFirstRateUseCaseTest {
  @Mock
  private FirstRatePort firstRatePort;
  @Mock
  private EventSender eventSender;

  private AppProperties appProperties;
  private FetchAssetsFromFirstRateUseCase useCase;

  @BeforeEach
  void setUp() {
    appProperties = new AppProperties();
    appProperties.getProvider().setKey("firstrate");
    appProperties.getProvider().setId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    appProperties.getFirstrate().setListingTypes(List.of("stock"));
    useCase = new FetchAssetsFromFirstRateUseCase(firstRatePort, eventSender, appProperties);
  }

  @Test
  void publishesOneResponsePerTickerWithLastFlag() {
    when(firstRatePort.listTickers("stock")).thenReturn(List.of(
        new FirstRateTicker("AAPL", "Apple", "2000-01-01", "", "stock"),
        new FirstRateTicker("MSFT", "Microsoft", "2000-01-01", "", "stock")
    ));
    when(eventSender.send(anyString(), any(), anyString(), anyMap())).thenReturn(true);

    useCase.fetch("corr-1", "assets.fetch.response");

    final ArgumentCaptor<FetchAssetsResponse> captor =
        ArgumentCaptor.forClass(FetchAssetsResponse.class);
    verify(eventSender, times(2)).send(
        eq("assets.fetch.response"),
        captor.capture(),
        eq(EventTypes.FETCH_ASSETS_RESPONSE),
        anyMap()
    );
    assertThat(captor.getAllValues()).hasSize(2);
    assertThat(captor.getAllValues().get(0).getIsLast()).isFalse();
    assertThat(captor.getAllValues().get(1).getIsLast()).isTrue();
    assertThat(captor.getAllValues().get(0).getAssetMetaDatas().getAsset().getSymbol())
        .isEqualTo("AAPL");
    assertThat(FetchAssetsFromFirstRateUseCase.mapPrimaryClass("fx"))
        .isEqualTo(PrimaryClass.FIAT);
  }
}
