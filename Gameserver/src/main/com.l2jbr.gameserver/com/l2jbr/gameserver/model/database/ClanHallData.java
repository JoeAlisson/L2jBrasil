package com.l2jbr.gameserver.model.database;


import com.l2jbr.commons.database.model.Entity;
import org.springframework.data.annotation.Id;

public class ClanHallData extends Entity<Integer> {

    @Id
    private Integer id;
    private String name;
    private Integer ownerId;
    private Integer lease;
    private String desc;
    private String location;
    private Long paidUntil;
    private Integer Grade;
    private Integer paid;

    @Override
    public Integer getId() {
        return id;
    }

    public Integer getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public Integer getLease() {
        return lease;
    }

    public String getDesc() {
        return desc;
    }

    public String getLocation() {
        return location;
    }

    public Long getPaidUntil() {
        return paidUntil;
    }

    public Integer getGrade() {
        return Grade;
    }

    public Integer getPaid() {
        return paid;
    }
}
