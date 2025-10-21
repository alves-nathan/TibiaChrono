package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="characters")
public class Character {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Sex sex;

    @Column(name="vocation_id")
    private Integer vocationId; // FK to vocation.id

    @Column(name = "level")
    private Integer level;

    @Column(name = "achievement_points")
    private Integer achievementPoints;

    @Column(name = "residence")
    private String residence;

    @Column(name = "last_login")
    private OffsetDateTime lastLogin;

    @Column(name = "acc_status")
    private String accStatus;

    @Column(name = "creation_date")
    private Instant creationDate;

    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL)
    private List<CharacterName> names = new ArrayList<>();

    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL)
    private List<CharacterWorld> worlds = new ArrayList<>();

    public enum Sex { male, female }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Sex getSex() {
        return sex;
    }

    public void setSex(Sex sex) {
        this.sex = sex;
    }

    public Integer getVocationId() {
        return vocationId;
    }

    public void setVocationId(Integer vocationId) {
        this.vocationId = vocationId;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Integer getAchievementPoints() {
        return achievementPoints;
    }

    public void setAchievementPoints(Integer achievementPoints) {
        this.achievementPoints = achievementPoints;
    }

    public String getResidence() {
        return residence;
    }

    public void setResidence(String residence) {
        this.residence = residence;
    }

    public OffsetDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(OffsetDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    public String getAccStatus() {
        return accStatus;
    }

    public void setAccStatus(String accStatus) {
        this.accStatus = accStatus;
    }

    public Instant getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Instant creationDate) {
        this.creationDate = creationDate;
    }

    public List<CharacterName> getNames() {
        return names;
    }

    public void setNames(List<CharacterName> names) {
        this.names = names;
    }

    public List<CharacterWorld> getWorlds() {
        return worlds;
    }

    public void setWorlds(List<CharacterWorld> worlds) {
        this.worlds = worlds;
    }

    public CharacterName getActiveName() {
        for (CharacterName cn : names) {
            if (Boolean.TRUE.equals(cn.getActive())) {
                return cn;
            }
        }
        return null;
    }
}