package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.CharacterDetailPort;
import org.jsoup.HttpStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JsoupCharacterAdapter implements CharacterDetailPort {
    private final TibiaCharacterHttpClient httpClient;
    private final CharacterDetailsPageParser parser;

    public JsoupCharacterAdapter() {
        this(new TibiaCharacterHttpClient(), new CharacterDetailsPageParser());
    }

    @Autowired
    JsoupCharacterAdapter(TibiaCharacterHttpClient httpClient, CharacterDetailsPageParser parser) {
        this.httpClient = httpClient;
        this.parser = parser;
    }

    @Override
    public NameDetails fetchNameDetails(String worldName, String characterName) {
        return fetchCharacterDetails(characterName)
                .map(details -> new NameDetails(details.currentName(), details.formerNames()))
                .orElseGet(() -> new NameDetails(characterName, List.of()));
    }

    @Override
    public Optional<CharacterDetails> fetchCharacterDetails(String characterName) {
        try {
            return parser.parse(httpClient.fetchCharacterDetailsDocument(characterName), characterName);
        } catch (HttpStatusException e) {
            throw new RuntimeException(
                    "Failed to fetch character details for " + characterName + ": HTTP " + e.getStatusCode(),
                    e
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch character details for " + characterName, e);
        }
    }

    Optional<CharacterDetails> parseCharacterDetailsHtml(String html, String characterName) {
        return parser.parseHtml(html, characterName);
    }
}
