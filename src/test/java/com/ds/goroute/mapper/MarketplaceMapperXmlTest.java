package com.ds.goroute.mapper;

import com.ds.goroute.config.database.UUIDTypeHandler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
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
}
