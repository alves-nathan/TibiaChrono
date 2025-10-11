package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;

@Entity @Table(name = "worlds")
public class World {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable=false, unique=true)
    private String name;
    @Column(name = "pvp_type")
    private String pvptype;
    @Column(name = "location")
    private String location;

    public World(){}
    public World(String name, String pvptype, String location){this.name=name;this.pvptype=pvptype;this.location=location;}

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

    public String getPvptype() {
        return pvptype;
    }

    public void setPvptype(String pvptype) {
        this.pvptype = pvptype;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}