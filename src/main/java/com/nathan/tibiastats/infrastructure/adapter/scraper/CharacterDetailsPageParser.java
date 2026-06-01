package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.CharacterDetailPort.CharacterDetails;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class CharacterDetailsPageParser {
    private static final Logger log = LoggerFactory.getLogger(CharacterDetailsPageParser.class);
    private static final String LOG_PREFIX = "[CHARACTER_DETAILS_SCRAPER]";

    private final CharacterProfileFieldsParser fieldsParser;
    private final CharacterDetailsValueParser valueParser;

    public CharacterDetailsPageParser() {
        this(new CharacterProfileFieldsParser(), new CharacterDetailsValueParser(new CharacterDetailsDateParser()));
    }

    @Autowired
    CharacterDetailsPageParser(
            CharacterProfileFieldsParser fieldsParser,
            CharacterDetailsValueParser valueParser
    ) {
        this.fieldsParser = fieldsParser;
        this.valueParser = valueParser;
    }

    public Optional<CharacterDetails> parseHtml(String html, String characterName) {
        Document doc = Jsoup.parse(html == null ? "" : html);
        return parse(doc, characterName);
    }

    public Optional<CharacterDetails> parse(Document doc, String characterName) {
        if (fieldsParser.isCharacterNotFound(doc)) {
            log.warn("{} Character not found on Tibia.com: {}", LOG_PREFIX, characterName);
            return Optional.empty();
        }

        Map<String, String> fields = fieldsParser.collectCharacterFields(doc);
        if (fields.isEmpty()) {
            log.warn("{} No character profile fields parsed for {}. title='{}'", LOG_PREFIX, characterName, doc.title());
        } else {
            log.debug("{} Parsed fields for {}: {}", LOG_PREFIX, characterName, fields.keySet());
        }

        return Optional.of(valueParser.toCharacterDetails(fields, characterName));
    }
}
