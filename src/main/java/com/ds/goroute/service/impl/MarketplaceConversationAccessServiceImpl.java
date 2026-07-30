package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.entity.*;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.*;
import com.ds.goroute.service.MarketplaceConversationAccessService;
import com.ds.goroute.service.PartnerAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class MarketplaceConversationAccessServiceImpl implements MarketplaceConversationAccessService {
    private final MarketplaceChatRepository chatRepository;
    private final HotelMarketplaceRepository hotelRepository;
    private final ActivityCommerceRepository activityRepository;
    private final PartnerAuthorizationService authorizationService;

    @Override
    public void requireAccess(UUID conversationId,UUID userId){
        MarketplaceConversation conversation=chatRepository.find(conversationId,userId)
                .orElseThrow(()->new BusinessException(ErrorConstant.NOT_FOUND,"Conversation not found"));
        if(conversation.getOrganizationId()==null){
            if(!chatRepository.canAccess(conversationId,userId))throw forbidden();
            return;
        }
        if(conversation.getHotelBookingId()!=null){
            HotelBooking booking=hotelRepository.findBooking(conversation.getHotelBookingId())
                    .orElseThrow(()->new BusinessException(ErrorConstant.NOT_FOUND,"Hotel booking not found"));
            if(userId.equals(booking.getUserId()))return;
            authorizationService.requireResourcePermission(conversation.getOrganizationId(),userId,"HOTEL",booking.getHotelId(),"CHAT_WRITE");
            return;
        }
        if(conversation.getActivityOrderId()!=null){
            ActivityOrder order=activityRepository.findOrder(conversation.getActivityOrderId())
                    .orElseThrow(()->new BusinessException(ErrorConstant.NOT_FOUND,"Activity order not found"));
            if(userId.equals(order.getUserId()))return;
            authorizationService.requireResourcePermission(conversation.getOrganizationId(),userId,"ACTIVITY",order.getActivityBookingId(),"CHAT_WRITE");
            return;
        }
        authorizationService.requirePermission(conversation.getOrganizationId(),userId,"CHAT_WRITE");
    }

    private BusinessException forbidden(){return new BusinessException(ErrorConstant.FORBIDDEN_ERROR,"You cannot access this conversation");}
}
