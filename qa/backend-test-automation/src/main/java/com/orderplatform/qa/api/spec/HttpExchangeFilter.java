package com.orderplatform.qa.api.spec;

import com.orderplatform.qa.util.Json;
import io.qameta.allure.Allure;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Captures HTTP exchanges for Allure while redacting credentials and limiting
 * attachment size. A custom filter is used instead of the stock Allure filter
 * so bearer tokens and cookies never end up in reports.
 */
public final class HttpExchangeFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(HttpExchangeFilter.class);
    private static final int MAX_BODY_CHARS = 50_000;
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key"
    );

    private final boolean logRequests;

    public HttpExchangeFilter(boolean logRequests) {
        this.logRequests = logRequests;
    }

    @Override
    public Response filter(FilterableRequestSpecification request,
                           FilterableResponseSpecification responseSpecification,
                           FilterContext context) {
        long startedAt = System.nanoTime();
        String endpoint = request.getMethod() + " " + sanitizeUri(request.getURI());
        try {
            Response response = context.next(request, responseSpecification);
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            if (logRequests) {
                log.info("{} -> {} ({} ms)", endpoint, response.statusCode(), elapsedMs);
            } else {
                log.debug("{} -> {} ({} ms)", endpoint, response.statusCode(), elapsedMs);
            }
            attachRequest(request, endpoint);
            attachResponse(response, endpoint, elapsedMs);
            return response;
        } catch (RuntimeException exception) {
            safeAttach("HTTP request failed: " + endpoint, requestDump(request));
            throw exception;
        }
    }

    private void attachRequest(FilterableRequestSpecification request, String endpoint) {
        safeAttach("HTTP request: " + endpoint, requestDump(request));
    }

    private void attachResponse(Response response, String endpoint, long elapsedMs) {
        StringBuilder dump = new StringBuilder()
                .append("Status: ").append(response.statusCode()).append('\n')
                .append("Duration: ").append(elapsedMs).append(" ms\n")
                .append("Headers:\n").append(headersDump(response.getHeaders()))
                .append('\n')
                .append(truncate(response.getBody().asString()));
        safeAttach("HTTP response: " + response.statusCode() + " " + endpoint, dump.toString());
    }

    private static String requestDump(FilterableRequestSpecification request) {
        StringBuilder dump = new StringBuilder()
                .append(request.getMethod()).append(' ').append(sanitizeUri(request.getURI())).append('\n')
                .append("Headers:\n").append(headersDump(request.getHeaders()))
                .append('\n')
                .append(bodyDump(request.getBody()));
        return dump.toString();
    }

    private static String headersDump(Iterable<Header> headers) {
        StringBuilder dump = new StringBuilder();
        for (Header header : headers) {
            String value = SENSITIVE_HEADERS.contains(header.getName().toLowerCase(Locale.ROOT))
                    ? "<redacted>"
                    : header.getValue();
            dump.append(header.getName()).append(": ").append(value).append('\n');
        }
        return dump.toString();
    }

    private static String bodyDump(Object body) {
        if (body == null) {
            return "";
        }
        String text;
        if (body instanceof byte[] bytes) {
            text = new String(bytes, StandardCharsets.UTF_8);
        } else if (body instanceof String string) {
            text = string;
        } else {
            try {
                text = Json.pretty(body);
            } catch (RuntimeException exception) {
                text = String.valueOf(body);
            }
        }
        return truncate(text);
    }

    private static String sanitizeUri(String uri) {
        return uri.replaceAll("(?i)([?&](?:token|api_key|apikey|password|secret)=)[^&]*", "$1***");
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_BODY_CHARS
                ? value
                : value.substring(0, MAX_BODY_CHARS) + "\n...<truncated>";
    }

    private static void safeAttach(String name, String content) {
        try {
            Allure.addAttachment(name, "text/plain", content, ".txt");
        } catch (RuntimeException exception) {
            log.debug("Could not attach HTTP exchange to Allure", exception);
        }
    }
}
