package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;

@Entity @Table(name="characternames")
public class CharacterName {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id; // also referenced by character.nameId
    @Column(nullable=false, unique=true)
    private String name;
    @Column(name = "active")
    private Boolean active;


    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}