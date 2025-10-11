package com.nathan.tibiastats.controller;

import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.model.ScrapeRecord;
import com.nathan.tibiastats.infrastructure.persistence.SpringWorldRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WorldControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired SpringWorldRepository repo;

    @Test
    void getWorldNow() throws Exception {
        var w = repo.save(new World("Antica","Open PvP","Europe"));
        repo.saveScrape(new ScrapeRecord(w, Instant.now(), 321, "[]"));
        mockMvc.perform(get("/api/online/worlds/Antica").header("Authorization","Bearer test.test.test"))
                .andExpect(status().isUnauthorized()); // token invalid
    }
}