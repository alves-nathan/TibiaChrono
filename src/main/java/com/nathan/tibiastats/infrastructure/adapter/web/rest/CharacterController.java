package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.domain.model.*;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.*; import java.util.stream.*;

@RestController
@RequestMapping("/api/character")
public class CharacterController {
    private final CharacterRepositoryPort repo;
    public CharacterController(CharacterRepositoryPort r){this.repo=r;}

    @GetMapping("/{name}")
    public Map<String,Object> getCharacter(@PathVariable String name){
        var c = repo.findByActiveName(name).orElseThrow();
        // Minimal fields; extend as you start filling CharacterEntity fields
        return Map.of("id", c.getId(), "name", name, "level", c.getLevel(), "vocationId", c.getVocationId());
    }

    @GetMapping("/{name}/stats")
    public List<Map<String, Serializable>> getStats(@PathVariable String name,
                                                    @RequestParam StatCategory category) {
        var c = repo.findByActiveName(name).orElseThrow();
        return repo.findStatsBy(c, category).stream()
                .map(r -> {
                    Map<String, Serializable> m = new HashMap<>();
                    m.put("date", r.getDate().toString());
                    m.put("value", r.getValue());
                    m.put("rank", r.getRank());
                    m.put("world", r.getWorld().getName());
                    return m;
                })
                .collect(Collectors.toList());
    }
}