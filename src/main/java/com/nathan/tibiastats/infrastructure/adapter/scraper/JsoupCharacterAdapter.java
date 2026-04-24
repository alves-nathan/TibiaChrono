package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.port.CharacterDetailPort;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JsoupCharacterAdapter implements CharacterDetailPort {

    // Example URL pattern for character info page (you must confirm)
    private static final String CHARACTER_PAGE_URL_TEMPLATE =
            "https://www.tibia.com/community/?subtopic=characters&name=%s&world=%s";

    public CharacterEntity fetchCharacterDetails() {

    }
}
