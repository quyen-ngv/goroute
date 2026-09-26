package com.ds.goroute.mapper;

import com.ds.goroute.config.database.UUIDTypeHandler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceMapperXmlTest {
    @Test
    void allMarketplaceMapperXmlFilesAreValidMyBatisMappings() throws Exception {
        Configuration configuration=new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class,UUIDTypeHandler.class);
        List<String> resources=List.of(
                "mapper/HostOrganizationMapper.xml",
                "mapper/PlaceSourceMapper.xml",
                "mapper/PartnerPlaceMapper.xml",
                "mapper/HotelMarketplaceMapper.xml",
                "mapper/ActivityCommerceMapper.xml",
                "mapper/MarketplaceChatMapper.xml",
                "mapper/MarketplaceReviewResponseMapper.xml",
                "mapper/MarketplaceHistoryMapper.xml",
                "mapper/AppConfigMapper.xml");
        for(String resource:resources){
            try(InputStream input=Resources.getResourceAsStream(resource)){
                new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
            }
        }
        assertTrue(configuration.hasStatement("com.ds.goroute.mapper.HotelMarketplaceMapper.reserveInventory"));
        assertTrue(configuration.hasStatement("com.ds.goroute.mapper.ActivityCommerceMapper.reserveSlot"));
        assertTrue(configuration.hasStatement("com.ds.goroute.mapper.MarketplaceChatMapper.insertMessage"));
        assertTrue(configuration.hasStatement("com.ds.goroute.mapper.AppConfigMapper.findAdmin"));
    }

    @Test
    void inventoryInsertRejectsBlockedUnitsAboveTheRoomTotal() throws Exception {
        try (InputStream input = Resources.getResourceAsStream("mapper/HotelMarketplaceMapper.xml")) {
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("AND COALESCE(#{blockedUnits},0) &lt;= COALESCE(#{totalUnits},r.total_units)"));
        }
    }

    @Test
    void inventoryUpsertPersistsTheInventoryOverridesAcceptedByTheApi() throws Exception {
        try (InputStream input = Resources.getResourceAsStream("mapper/HotelMarketplaceMapper.xml")) {
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            int statementStart = xml.indexOf("<insert id=\"upsertInventoryRange\">");
            int statementEnd = xml.indexOf("</insert>", statementStart);
            String statement = xml.substring(statementStart, statementEnd);

            assertTrue(statement.contains("#{priceOverride},#{minStay},COALESCE(#{closedToArrival},false),COALESCE(#{closedToDeparture},false)"));
            assertTrue(statement.contains("price_override=COALESCE(#{priceOverride},room_inventory_daily.price_override)"));
            assertTrue(statement.contains("min_stay=COALESCE(#{minStay},room_inventory_daily.min_stay)"));
            assertTrue(statement.contains("closed_to_arrival=COALESCE(#{closedToArrival},room_inventory_daily.closed_to_arrival)"));
            assertTrue(statement.contains("closed_to_departure=COALESCE(#{closedToDeparture},room_inventory_daily.closed_to_departure)"));
        }
    }

    @Test
    void chatMapperDeclaresTheStatementsTheChatScreenNeeds() throws Exception {
        Configuration configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        try (InputStream input = Resources.getResourceAsStream("mapper/MarketplaceChatMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "mapper/MarketplaceChatMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        for (String statement : List.of("findByTrip", "findNotifiableMemberIds", "findActiveMemberIds",
                "markMemberLeft", "updateMuted", "updatePinnedMessage", "countUnreadConversationsForUser",
                "findLatestMessages", "searchMessages", "softDeleteOwnMessage", "updateMessageContent",
                "insertReaction", "deleteReaction", "findReactions")) {
            assertTrue(configuration.hasStatement("com.ds.goroute.mapper.MarketplaceChatMapper." + statement),
                    "missing statement " + statement);
        }
    }

    /**
     * The privacy rule, in the query rather than only in the service: an operator list that
     * gains a filter or a sort must not be able to produce a trip group or a person-to-person
     * thread by accident.
     */
    @Test
    void theOperatorConversationListExcludesPrivateThreadsInSql() throws Exception {
        String xml = chatMapperXml();
        int start = xml.indexOf("<select id=\"findAdmin\"");
        String statement = xml.substring(start, xml.indexOf("</select>", start));

        assertTrue(statement.contains("c.conversation_type &lt;&gt; 'TRIP'"));
        assertTrue(statement.contains("NOT (c.conversation_type='DIRECT' AND c.organization_id IS NULL)"));
    }

    @Test
    void chatWritesCarryTheNewColumns() throws Exception {
        String xml = chatMapperXml();
        int conversation = xml.indexOf("<insert id=\"insertConversation\">");
        assertTrue(xml.substring(conversation, xml.indexOf("</insert>", conversation)).contains("trip_id"));

        int message = xml.indexOf("<insert id=\"insertMessage\">");
        assertTrue(xml.substring(message, xml.indexOf("</insert>", message)).contains("reply_to_message_id"));
    }

    /** A retired trip keeps its transcript but leaves the inbox. */
    @Test
    void theInboxHidesArchivedThreads() throws Exception {
        String xml = chatMapperXml();
        int start = xml.indexOf("<select id=\"findForUser\"");
        assertTrue(xml.substring(start, xml.indexOf("</select>", start)).contains("c.status &lt;&gt; 'ARCHIVED'"));
    }

    /** Read markers only ever move forward, so an old screen cannot un-read a thread. */
    @Test
    void theReadMarkerNeverMovesBackwards() throws Exception {
        String xml = chatMapperXml();
        int start = xml.indexOf("<update id=\"markRead\">");
        assertTrue(xml.substring(start, xml.indexOf("</update>", start)).contains("&gt;COALESCE((SELECT sequence_no"));
    }

    private String chatMapperXml() throws Exception {
        try (InputStream input = Resources.getResourceAsStream("mapper/MarketplaceChatMapper.xml")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
