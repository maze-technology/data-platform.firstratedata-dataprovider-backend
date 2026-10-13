package tech.maze.data.firstrate.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * First Rate Data provider entry point.
 */
@SpringBootApplication
public class App {
  /**
   * Boots the First Rate Data provider.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(App.class, args);
  }
}
