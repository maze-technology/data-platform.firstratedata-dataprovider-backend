package tech.maze.data.firstrate.backend;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tech.maze.commons.CommonsConfiguration;

/**
 * Application configuration for the First Rate Data provider.
 */
@Configuration
@Import({CommonsConfiguration.class})
@ComponentScan("tech.maze.data.firstrate.backend")
@EnableConfigurationProperties(AppProperties.class)
public class AppConfiguration {
}
