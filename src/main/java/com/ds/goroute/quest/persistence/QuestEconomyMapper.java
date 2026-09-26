package com.ds.goroute.quest.persistence;

import com.ds.goroute.quest.domain.QuestEarning;
import com.ds.goroute.quest.domain.QuestTip;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/** Persistence for creator income and tips (§3.5). */
@Mapper
public interface QuestEconomyMapper {

    void insertEarning(QuestEarning earning);

    QuestEarning findEarningByReference(@Param("reference") String reference);

    void insertTip(QuestTip tip);
}
