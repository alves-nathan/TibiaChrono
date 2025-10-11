package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.OffsetDateTime;

@Entity
@Table(name="characters")
public class CharacterEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name="nameId")
    private Integer nameId; // FK to characternames.id (current active name)

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

    public enum Sex { male, female }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getNameId() {
        return nameId;
    }

    public void setNameId(Integer nameId) {
        this.nameId = nameId;
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

    public String getResidence() {
        return residence;
    }

    public void setResidence(String residence) {
        this.residence = residence;
    }
}