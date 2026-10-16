package com.atoconn.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Keeps the reviewed Trivy ignores for CVE-2026-47884 and CVE-2026-47890 honest.
 *
 * <p>CVE-2026-47884 (spring-webmvc, RCE via XsltView) is only exploitable when the application
 * renders server-side views and a request can steer the view name. The free fix is Spring
 * Framework 7.0.9 (Spring Boot 4); the 6.2.x fix is commercial-only, so the release gate carries a
 * reviewed {@code .trivyignore} entry instead. That entry is valid only while this service stays
 * REST-only: no XSLT/template views, no view resolvers, no view-returning {@code @Controller}s.
 *
 * <p>CVE-2026-47890 (spring-webmvc, stream corruption in Server-Sent Events built from view
 * fragments) has the same fix situation and needs SSE/streamed responses to be reachable, so the
 * guard also rejects SSE emitters, view fragments and event-stream endpoints.
 *
 * <p>This test fails the build the moment any of that is introduced, so the ignore cannot silently
 * become wrong. The Docker build runs it explicitly (see Dockerfile).
 */
class ViewRenderingReachabilityGuardTest {

    /** Source constructs that bring server-side view rendering (and the CVE's attack path) back. */
    private static final List<Pattern> FORBIDDEN_SOURCE = List.of(
            Pattern.compile("\\bXsltView(Resolver)?\\b"),
            Pattern.compile("\\b[A-Za-z]*ViewResolver\\b"),
            Pattern.compile("\\bModelAndView\\b"),
            Pattern.compile("^\\s*@Controller\\b", Pattern.MULTILINE),
            Pattern.compile("\\b(SseEmitter|ServerSentEvent|FragmentsRendering|ResponseBodyEmitter)\\b"),
            Pattern.compile("\\bTEXT_EVENT_STREAM(_VALUE)?\\b|text/event-stream"));

    /** View templates/stylesheets that would only exist if views were rendered. */
    private static final Pattern FORBIDDEN_RESOURCE =
            Pattern.compile(".*(\\.xslt?|/templates/.+)$");

    @Test
    void serviceHasNoServerSideViewRendering() {
        assertThat(violations(reactorRoot()))
                .as("CVE-2026-47884/47890 are ignored in .trivyignore only because this service renders "
                        + "no views and streams no SSE. Remove that code, or remove the ignores and upgrade to "
                        + "Spring Framework 7.0.9+")
                .isEmpty();
    }

    @Test
    void guardDetectsXsltViewInMainSources(@TempDir Path root) throws IOException {
        write(root.resolve("svc/src/main/java/a/Cfg.java"),
                "import org.springframework.web.servlet.view.xslt.XsltView;\nclass Cfg {}\n");

        assertThat(violations(root)).singleElement().asString().contains("Cfg.java");
    }

    @Test
    void guardDetectsViewControllersAndTemplates(@TempDir Path root) throws IOException {
        write(root.resolve("svc/src/main/java/a/Page.java"), "@Controller\nclass Page {}\n");
        write(root.resolve("svc/src/main/resources/templates/page.html"), "<p/>");
        write(root.resolve("svc/src/main/resources/report.xsl"), "<xsl:stylesheet/>");

        assertThat(violations(root)).hasSize(3);
    }

    @Test
    void guardDetectsServerSentEventStreams(@TempDir Path root) throws IOException {
        write(root.resolve("svc/src/main/java/a/Feed.java"),
                "class Feed { SseEmitter stream() { return new SseEmitter(); } }\n");
        write(root.resolve("svc/src/main/java/a/Fragments.java"), "class Fragments { FragmentsRendering r; }\n");
        write(root.resolve("svc/src/main/java/a/Events.java"),
                "@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)\nclass Events {}\n");

        assertThat(violations(root)).hasSize(3);
    }

    @Test
    void guardIgnoresRestControllersAndTestSources(@TempDir Path root) throws IOException {
        write(root.resolve("svc/src/main/java/a/Api.java"),
                "@RestController\n@ControllerAdvice\nclass Api {}\n");
        write(root.resolve("svc/src/test/java/a/T.java"), "class T { ModelAndView m; }\n");

        assertThat(violations(root)).isEmpty();
    }

    private static List<String> violations(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> unixPath(p).contains("/src/main/"))
                    .filter(ViewRenderingReachabilityGuardTest::violates)
                    .map(p -> root.relativize(p).toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean violates(Path file) {
        String path = unixPath(file);
        if (FORBIDDEN_RESOURCE.matcher(path).matches()) {
            return true;
        }
        if (!path.endsWith(".java")) {
            return false;
        }
        String source = read(file);
        return FORBIDDEN_SOURCE.stream().anyMatch(p -> p.matcher(source).find());
    }

    /** Single-module service: the working directory is the project root. */
    private static Path reactorRoot() {
        Path module = Path.of("").toAbsolutePath();
        assertThat(module.resolve("pom.xml")).as("project root pom at %s", module).exists();
        return module;
    }

    private static String unixPath(Path p) {
        return p.toAbsolutePath().toString().replace('\\', '/');
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
