package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name="characters")
public class CharacterEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public enum Sex { male, female }

    @Enumerated(EnumType.STRING)
    private Sex sex;

    @ManyToOne @JoinColumn(name = "vocation_id")
    private Vocation vocation;

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

    @OneToMany(mappedBy = "character", cascade = CascadeType.PERSIST, orphanRemoval = false)
    private Set<CharacterName> names = new HashSet<>();

    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL)
    private Set<CharacterWorld> worlds = new HashSet<>();



    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Sex getSex() {
        return sex;
    }

    public void setSex(Sex sex) {
        this.sex = sex;
    }

    public Vocation getVocation() {
        return vocation;
    }

    public void setVocation(Vocation vocation) {
        this.vocation = vocation;
    }

    public Set<CharacterName> getNames() {
        return names;
    }

    public void setNames(Set<CharacterName> names) {
        this.names = names;
    }

    public Set<CharacterWorld> getWorlds() {
        return worlds;
    }

    public void setWorlds(Set<CharacterWorld> worlds) {
        this.worlds = worlds;
    }

    public void addName(CharacterName n){
        n.setCharacter(this);
        names.add(n);
    }
}