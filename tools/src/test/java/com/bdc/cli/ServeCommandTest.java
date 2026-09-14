package com.bdc.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises {@link ServeCommand}'s static file server directly, without the blocking CLI loop. */
class ServeCommandTest {

  @TempDir Path dir;

  private HttpServer server;
  private HttpClient client;
  private String base;

  @BeforeEach
  void start() throws IOException {
    Files.writeString(dir.resolve("index.html"), "<html>home</html>");
    Files.writeString(dir.resolve("styles.css"), "body { color: black; }");
    Files.createDirectories(dir.resolve("US-NYSE"));
    Files.writeString(dir.resolve("US-NYSE").resolve("index.html"), "<html>nyse market</html>");
    Files.writeString(dir.resolve("data.json"), "{\"ok\":true}");

    server = ServeCommand.start(dir, 0);
    int port = server.getAddress().getPort();
    base = "http://localhost:" + port;
    client = HttpClient.newHttpClient();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void servesTheIndexAtTheRoot() throws Exception {
    HttpResponse<String> response = get("/");
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("home"));
    assertTrue(
        response.headers().firstValue("Content-Type").orElse("").contains("text/html"),
        "expected an HTML content type");
  }

  @Test
  void resolvesADirectoryToItsIndex() throws Exception {
    HttpResponse<String> response = get("/US-NYSE/");
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("nyse market"));
  }

  @Test
  void returns404ForAMissingFile() throws Exception {
    HttpResponse<String> response = get("/does-not-exist/");
    assertEquals(404, response.statusCode());
    assertTrue(response.body().contains("404"), "expected a 404 body");
  }

  @Test
  void refusesAPathTraversalAttempt() throws Exception {
    // Percent-encoded so the traversal survives to the server rather than being collapsed by the
    // HTTP client while building the request.
    HttpResponse<String> response = get("/%2e%2e/%2e%2e/%2e%2e/etc/passwd");
    assertEquals(404, response.statusCode(), "a path escaping --dir must never be served");
  }

  @Test
  void setsTheContentTypeByExtension() throws Exception {
    HttpResponse<String> css = get("/styles.css");
    assertEquals(200, css.statusCode());
    assertTrue(css.headers().firstValue("Content-Type").orElse("").contains("text/css"));

    HttpResponse<String> json = get("/data.json");
    assertEquals(200, json.statusCode());
    assertTrue(json.headers().firstValue("Content-Type").orElse("").contains("application/json"));
  }

  private HttpResponse<String> get(String path) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(URI.create(base + path)).GET().build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
