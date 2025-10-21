package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;

import java.time.LocalDate;

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
    @Column(name = "online_record")
    private String onlineRecord;
    @Column(name = "creation_date")
    private LocalDate creationDate;
    @Column(name = "transfer_type")
    private String transferType;
    @Column(name = "game_world_type")
    private String gameWorldType;

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

    public String getOnlineRecord() {
        return onlineRecord;
    }

    public void setOnlineRecord(String onlineRecord) {
        this.onlineRecord = onlineRecord;
    }

    public LocalDate getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDate creationDate) {
        this.creationDate = creationDate;
    }

    public String getTransferType() {
        return transferType;
    }

    public void setTransferType(String transferType) {
        this.transferType = transferType;
    }

    public String getGameWorldType() {
        return gameWorldType;
    }

    public void setGameWorldType(String gameWorldType) {
        this.gameWorldType = gameWorldType;
    }
}