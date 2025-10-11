package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.port.HighscorePort;
import org.jsoup.Jsoup; import org.jsoup.nodes.Document; import org.jsoup.nodes.Element; import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import java.util.*; import java.io.IOException;

@Component
public class JsoupHighscoreAdapter implements HighscorePort {
    private static final String HS_URL = "https://www.tibia.com/community/?subtopic=highscores&world=%s&beprotection=-1&profession=%d&category=%d&currentpage=%d";

    @Override
    public List<HighscoreRow> fetchHighscores(String world, StatCategory category, int vocationId, int page) {
        int catId = mapCategory(category);
        String url = String.format(HS_URL, world, vocationId, catId, page);
        try {
            Document doc = Jsoup.connect(url).get();
            List<HighscoreRow> out = new ArrayList<>();
            Elements rows = doc.select("table.TableContent tr");
            for (Element tr : rows){
                Elements tds = tr.select("td");
                if (tds.size() < 3) continue;
                int rank = parseIntSafe(tds.get(0).text());
                String name = tds.get(1).text().trim();
                long value = parseLongSafe(tds.get(2).text());
                if (rank > 0 && !name.isBlank()) out.add(new HighscoreRow(rank, name, value));
            }
            return out;
        } catch (IOException e){ throw new RuntimeException(e); }
    }

    private int mapCategory(StatCategory c){
        return switch (c){
            case ACHIEVEMENTS -> 1; case AXE_FIGHTING -> 2; case CHARM_POINTS -> 3; case CLUB_FIGHTING -> 4;
            case DISTANCE_FIGHTING -> 5; case EXPERIENCE -> 6; case FISHING -> 7; case FIST_FIGHTING -> 8;
            case GOSHNARS_TAINT -> 9; case LOYALTY_POINTS -> 10; case MAGIC_LEVEL -> 11; case SHIELDING -> 12;
            case SWORD_FIGHTING -> 13; case DROME_SCORE -> 14; case BOSS_POINTS -> 15; };
    }
    private int parseIntSafe(String s){ try { return Integer.parseInt(s.replaceAll("[^0-9]","")); } catch(Exception e){ return 0; } }
    private long parseLongSafe(String s){ try { return Long.parseLong(s.replaceAll("[^0-9]","")); } catch(Exception e){ return 0L; } }
}