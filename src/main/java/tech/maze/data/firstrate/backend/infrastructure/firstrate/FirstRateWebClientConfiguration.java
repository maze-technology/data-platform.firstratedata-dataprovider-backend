package tech.maze.data.firstrate.backend.infrastructure.firstrate;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient beans for First Rate HTTP calls.
 */
@Configuration
public class FirstRateWebClientConfiguration {
  /**
   * Shared WebClient builder.
   */
  @Bean
  public WebClient.Builder firstrateWebClientBuilder() {
    return WebClient.builder();
  }
}
