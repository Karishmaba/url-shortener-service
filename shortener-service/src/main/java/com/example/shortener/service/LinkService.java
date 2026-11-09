package com.example.shortener.service;

import com.example.shortener.domain.CodeCollisionException;
import com.example.shortener.domain.CodeGenerator;
import com.example.shortener.domain.CodeUnavailableException;
import com.example.shortener.domain.LinkNotFoundException;
import com.example.shortener.domain.LinkStats;
import com.example.shortener.domain.ShortLink;
import com.example.shortener.domain.UrlValidator;
import com.example.shortener.persistence.LinkRepository;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class LinkService {

    private static final int MAX_CODE_ATTEMPTS = 10;
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9]{8}");

    private final LinkRepository repository;
    private final UrlValidator validator;
    private final CodeGenerator generator;

    public LinkService(LinkRepository repository, UrlValidator validator, CodeGenerator generator) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    /** Creates a separate link on every call, preserving the validated destination. */
    public ShortLink create(String url) {
        String destination = validator.validate(url);
        // Each repository insert is independent; never wrap this retry loop in a transaction.
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = generator.nextCode();
            try {
                repository.insert(code, destination);
            } catch (CodeCollisionException collision) {
                continue;
            }
            return new ShortLink(code, destination, 0L);
        }
        throw new CodeUnavailableException();
    }

    /** Returns only after the repository has committed exactly one GET increment. */
    public String redirect(String code) {
        validateCode(code);
        return repository.incrementAndGetDestination(code).orElseThrow(LinkNotFoundException::new);
    }

    /** Resolves a destination for HEAD without incrementing its counter. */
    public String resolve(String code) {
        return find(code).destination();
    }

    public LinkStats stats(String code) {
        ShortLink link = find(code);
        return new LinkStats(link.code(), link.redirectCount());
    }

    public void health() {
        repository.checkConnectivity();
    }

    private ShortLink find(String code) {
        validateCode(code);
        return repository.find(code).orElseThrow(LinkNotFoundException::new);
    }

    private static void validateCode(String code) {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new LinkNotFoundException();
        }
    }
}
