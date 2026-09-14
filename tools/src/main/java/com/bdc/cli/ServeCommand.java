package com.bdc.cli;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Serves a directory of static files over HTTP — for previewing {@code tools site} output (or any
 * other static directory) without a browser's {@code file://} restrictions on relative fetches.
 *
 * <p>Built entirely on the JDK's bundled {@code com.sun.net.httpserver.HttpServer}: zero
 * third-party dependencies, one file. Directories resolve to their {@code index.html}; anything
 * outside {@code --dir} is refused regardless of {@code ..} or encoded traversal in the request
 * path.
 */
@Command(
    name = "serve",
    description = "Serve a directory of static files over HTTP (e.g. tools site output)")
public class ServeCommand implements Callable<Integer> {

  @Option(
      names = {"--dir", "-d"},
      description = "Directory to serve",
      defaultValue = "site")
  private Path dir;

  @Option(
      names = {"--port", "-p"},
      description = "Port to listen on (0 picks a free port)",
      defaultValue = "8080")
  private int port;

  @Option(
      names = {"--open"},
      description = "Open the served site in the default browser")
  private boolean open;

  private static final Map<String, String> CONTENT_TYPES =
      Map.ofEntries(
          Map.entry("html", "text/html; charset=utf-8"),
          Map.entry("css", "text/css; charset=utf-8"),
          Map.entry("js", "application/javascript; charset=utf-8"),
          Map.entry("json", "application/json; charset=utf-8"),
          Map.entry("ics", "text/calendar; charset=utf-8"),
          Map.entry("xml", "application/xml; charset=utf-8"),
          Map.entry("txt", "text/plain; charset=utf-8"),
          Map.entry("svg", "image/svg+xml"),
          Map.entry("png", "image/png"));

  private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

  @Override
  public Integer call() throws IOException {
    if (!Files.isDirectory(dir)) {
      System.err.println("Not a directory: " + dir);
      return 1;
    }

    HttpServer server = start(dir, port);
    int boundPort = server.getAddress().getPort();
    String url = "http://localhost:" + boundPort + "/";
    System.out.println("Serving " + dir.toAbsolutePath().normalize() + " at " + url);
    System.out.println("Press Ctrl-C to stop.");
    if (open) {
      openBrowser(url);
    }

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  server.stop(0);
                  System.out.println("Server stopped.");
                }));

    CountDownLatch keepAlive = new CountDownLatch(1);
    try {
      keepAlive.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return 0;
  }

  /**
   * Starts a server rooted at {@code dir} on {@code port} (0 for an ephemeral port) and returns it,
   * already started. Exposed as a static entry point so tests can drive it directly without going
   * through the CLI's blocking {@link #call()}.
   */
  public static HttpServer start(Path dir, int port) throws IOException {
    Path root = dir.toAbsolutePath().normalize();
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
    server.createContext("/", exchange -> handle(exchange, root));
    server.setExecutor(null);
    server.start();
    return server;
  }

  private static void handle(HttpExchange exchange, Path root) {
    String method = exchange.getRequestMethod();
    String rawPath = exchange.getRequestURI().getPath();
    int status = 500;
    try (exchange) {
      if (!"GET".equals(method) && !"HEAD".equals(method)) {
        status = 405;
        sendBytes(
            exchange,
            status,
            "text/plain; charset=utf-8",
            "Method not allowed".getBytes(StandardCharsets.UTF_8),
            method);
        return;
      }

      Path resolved = resolve(root, rawPath);
      if (resolved != null && Files.isDirectory(resolved)) {
        resolved = resolved.resolve("index.html");
      }
      if (resolved == null || !Files.isRegularFile(resolved)) {
        status = 404;
        sendBytes(exchange, status, "text/html; charset=utf-8", notFoundBody(rawPath), method);
        return;
      }

      byte[] body = Files.readAllBytes(resolved);
      status = 200;
      sendBytes(exchange, status, contentTypeFor(resolved), body, method);
    } catch (IOException e) {
      status = 500;
    } finally {
      System.out.println(method + " " + rawPath + " " + status);
    }
  }

  /**
   * Resolves a request path against {@code root}, refusing anything that would escape it (a leading
   * {@code ..}, an encoded traversal — {@link HttpExchange#getRequestURI()} decodes
   * percent-encoding before this method ever sees the path). Returns null when the path escapes.
   */
  private static Path resolve(Path root, String rawPath) {
    String relative = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
    Path candidate = root.resolve(relative).normalize();
    if (!candidate.equals(root) && !candidate.startsWith(root)) {
      return null;
    }
    return candidate;
  }

  private static void sendBytes(
      HttpExchange exchange, int status, String contentType, byte[] body, String method)
      throws IOException {
    exchange.getResponseHeaders().set("Content-Type", contentType);
    boolean head = "HEAD".equals(method);
    exchange.sendResponseHeaders(status, head ? -1 : body.length);
    if (!head) {
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    }
  }

  private static byte[] notFoundBody(String rawPath) {
    String escaped = rawPath.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    String html =
        "<!doctype html><title>404 Not Found</title>"
            + "<h1>404 Not Found</h1><p>No file at <code>"
            + escaped
            + "</code>.</p>";
    return html.getBytes(StandardCharsets.UTF_8);
  }

  private static String contentTypeFor(Path path) {
    String name = path.getFileName().toString();
    int dot = name.lastIndexOf('.');
    if (dot < 0 || dot == name.length() - 1) {
      return DEFAULT_CONTENT_TYPE;
    }
    return CONTENT_TYPES.getOrDefault(name.substring(dot + 1).toLowerCase(), DEFAULT_CONTENT_TYPE);
  }

  private static void openBrowser(String url) {
    try {
      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI.create(url));
      } else {
        System.out.println("Cannot open a browser automatically; visit " + url);
      }
    } catch (Exception e) {
      System.out.println("Could not open a browser automatically: " + e.getMessage());
    }
  }
}
