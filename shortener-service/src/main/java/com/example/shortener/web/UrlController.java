package com.example.shortener.web;

import com.example.shortener.config.PublicBaseUrl;
import com.example.shortener.domain.InvalidUrlException;
import com.example.shortener.domain.LinkStats;
import com.example.shortener.domain.ShortLink;
import com.example.shortener.service.LinkService;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class UrlController {

    private final LinkService service;
    private final PublicBaseUrl publicBaseUrl;
    private final ObjectReader json;

    public UrlController(LinkService service, PublicBaseUrl publicBaseUrl, JsonMapper mapper) {
        this.service = service;
        this.publicBaseUrl = publicBaseUrl;
        this.json = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @PostMapping(path = "/api/urls", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreateUrlResponse> create(@RequestBody(required = false) byte[] body) {
        String destination = readDestination(body);
        ShortLink link = service.create(destination);
        String shortUrl = publicBaseUrl.shortUrl(link.code());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, shortUrl)
                .body(new CreateUrlResponse(link.code(), link.destination(), shortUrl));
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        return redirectResponse(service.redirect(code));
    }

    @RequestMapping(path = "/{code}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> head(@PathVariable String code) {
        return redirectResponse(service.resolve(code));
    }

    @GetMapping(path = "/api/urls/{code}/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public LinkStats stats(@PathVariable String code) {
        return service.stats(code);
    }

    @GetMapping(path = "/api/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> health() {
        service.health();
        return Map.of("status", "UP");
    }

    private String readDestination(byte[] body) {
        if (body == null || body.length == 0) {
            throw new InvalidJsonException();
        }
        final JsonNode root;
        try {
            root = json.readTree(body);
        } catch (JacksonException invalid) {
            throw new InvalidJsonException();
        }
        if (root == null || !root.isObject()) {
            throw new InvalidJsonException();
        }
        JsonNode url = root.get("url");
        if (url == null || !url.isString()) {
            throw new InvalidUrlException();
        }
        return url.stringValue();
    }

    private static ResponseEntity<Void> redirectResponse(String destination) {
        // A raw header preserves the stored destination without URI builder encoding.
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, destination)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    public record CreateUrlResponse(String code, String url, String shortUrl) {
    }
}
