package com.ds.goroute.service.impl;
import com.ds.goroute.constant.ErrorConstant;import com.ds.goroute.dto.request.*;import com.ds.goroute.dto.response.*;import com.ds.goroute.entity.*;import com.ds.goroute.exception.BusinessException;import com.ds.goroute.repository.*;import com.ds.goroute.service.*;import com.ds.goroute.type.MarketplaceConversationStatus;import com.ds.goroute.type.MarketplaceConversationType;import com.ds.goroute.type.MarketplaceMessageType;import com.ds.goroute.type.OrganizationMemberStatus;import com.fasterxml.jackson.core.JsonProcessingException;import com.fasterxml.jackson.core.type.TypeReference;import com.fasterxml.jackson.databind.ObjectMapper;import lombok.RequiredArgsConstructor;import org.springframework.dao.DataIntegrityViolationException;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import java.time.LocalDateTime;import java.util.*;
@Service @RequiredArgsConstructor
public class MarketplaceChatServiceImpl implements MarketplaceChatService {
 private final MarketplaceChatRepository repo;private final com.ds.goroute.mapper.MarketplaceChatMapper chatMapper;private final HotelMarketplaceRepository hotelRepo;private final ActivityCommerceRepository activityRepo;private final HostOrganizationRepository orgRepo;private final UserRepository userRepo;private final PartnerAuthorizationService authorization;private final MarketplaceHistoryService history;private final WebSocketService webSocketService;private final ObjectMapper mapper;private final BusinessConfigService businessConfig;private final com.ds.goroute.service.notification.ConversationNotifier conversationNotifier;private final com.ds.goroute.service.UserBlockService userBlocks;
 @Transactional public MarketplaceConversationResponse start(UUID actor,StartMarketplaceConversationRequest r){MarketplaceConversationType type=r.getConversationType();if(type==MarketplaceConversationType.HOTEL_BOOKING)return startHotel(actor,r.getHotelBookingId());if(type==MarketplaceConversationType.ACTIVITY_ORDER)return startActivity(actor,r.getActivityOrderId());return startDirect(actor,r);}
 private MarketplaceConversationResponse startHotel(UUID actor,UUID id){if(id==null)throw bad("hotelBookingId is required");MarketplaceConversation existing=repo.findByHotelBooking(id,actor).orElse(null);if(existing!=null){require(existing.getId(),actor);return response(existing);}HotelBooking b=hotelRepo.findBooking(id).orElseThrow(()->notFound("Hotel booking not found"));if(!actor.equals(b.getUserId()))authorization.requireResourcePermission(b.getOrganizationId(),actor,"HOTEL",b.getHotelId(),"CHAT_WRITE");MarketplaceConversation c=create(MarketplaceConversationType.HOTEL_BOOKING,b.getOrganizationId(),b.getId(),null);if(b.getUserId()!=null)repo.addMember(c.getId(),b.getUserId(),"GUEST",c.getCreatedAt());if(!actor.equals(b.getUserId()))repo.addMember(c.getId(),actor,"HOST",c.getCreatedAt());history.record(b.getOrganizationId(),"CONVERSATION",c.getId(),"CREATED",c,List.of(),actor,"USER",null);return response(repo.find(c.getId(),actor).orElse(c));}
 private MarketplaceConversationResponse startActivity(UUID actor,UUID id){if(id==null)throw bad("activityOrderId is required");MarketplaceConversation existing=repo.findByActivityOrder(id,actor).orElse(null);if(existing!=null){require(existing.getId(),actor);return response(existing);}ActivityOrder o=activityRepo.findOrder(id).orElseThrow(()->notFound("Activity order not found"));if(!actor.equals(o.getUserId()))authorization.requireResourcePermission(o.getOrganizationId(),actor,"ACTIVITY",o.getActivityBookingId(),"CHAT_WRITE");MarketplaceConversation c=create(MarketplaceConversationType.ACTIVITY_ORDER,o.getOrganizationId(),null,o.getId());if(o.getUserId()!=null)repo.addMember(c.getId(),o.getUserId(),"GUEST",c.getCreatedAt());if(!actor.equals(o.getUserId()))repo.addMember(c.getId(),actor,"HOST",c.getCreatedAt());history.record(o.getOrganizationId(),"CONVERSATION",c.getId(),"CREATED",c,List.of(),actor,"USER",null);return response(repo.find(c.getId(),actor).orElse(c));}
 private MarketplaceConversation create(MarketplaceConversationType type,UUID org,UUID booking,UUID order){LocalDateTime n=LocalDateTime.now();MarketplaceConversation c=MarketplaceConversation.builder().id(UUID.randomUUID()).conversationType(type.name()).organizationId(org).hotelBookingId(booking).activityOrderId(order).status(MarketplaceConversationStatus.OPEN.name()).createdAt(n).updatedAt(n).build();try{repo.insertConversation(c);}catch(DataIntegrityViolationException ex){MarketplaceConversation found=booking!=null?repo.findByHotelBooking(booking,null).orElse(null):repo.findByActivityOrder(order,null).orElse(null);if(found!=null)return found;throw ex;}return c;}
 public List<MarketplaceConversationResponse> listMine(UUID a,int p,int s){Page r=page(p,s);return responses(repo.findForUser(a,r.l,r.o));}
 public MarketplaceConversationResponse get(UUID a,UUID id){require(id,a);return response(conversation(id,a));}
 public List<MarketplaceMessageResponse> messages(UUID a,UUID id,Long after,int limit){require(id,a);return rawMessages(id,after,limit);}
 @Transactional public MarketplaceMessageResponse send(UUID a,UUID id,SendMarketplaceMessageRequest r){require(id,a);ensureConversationMember(id,a);ensureNotBlocked(id,a);if(r.getMessageType()==MarketplaceMessageType.SYSTEM)throw forbidden();if((r.getContent()==null||r.getContent().isBlank())&&(r.getAttachments()==null||r.getAttachments().isEmpty()))throw bad("Message content or attachment is required");MarketplaceMessage prior=repo.findMessageByClientId(id,a,r.getClientMessageId()).orElse(null);if(prior!=null)return messageResponse(prior);repo.lock(id);prior=repo.findMessageByClientId(id,a,r.getClientMessageId()).orElse(null);if(prior!=null)return messageResponse(prior);LocalDateTime n=LocalDateTime.now();MarketplaceMessage m=MarketplaceMessage.builder().id(UUID.randomUUID()).conversationId(id).senderUserId(a).clientMessageId(r.getClientMessageId()).messageType((r.getMessageType()==null?MarketplaceMessageType.TEXT:r.getMessageType()).name()).content(r.getContent()==null?null:r.getContent().trim()).attachments(json(r.getAttachments()==null?List.of():r.getAttachments())).sequenceNo(repo.nextSequence(id)).createdAt(n).build();repo.insertMessage(m);repo.updateLast(id,n);MarketplaceMessageResponse saved=messageResponse(repo.findMessage(m.getId()).orElse(m));broadcastAfterCommit(id,a,saved);conversationNotifier.notifyNewMessage(id,a,saved);return saved;}
 @Transactional public void markRead(UUID a,UUID id,UUID messageId){require(id,a);ensureConversationMember(id,a);MarketplaceMessage m=repo.findMessage(messageId).orElseThrow(()->notFound("Message not found"));if(!id.equals(m.getConversationId()))throw bad("Message does not belong to conversation");repo.markRead(id,a,messageId);}
 /**
  * The access decision used to run in Java over an already paged result, so a scoped
  * employee saw short or empty pages and could not reach older conversations at all.
  * It now runs in SQL before the LIMIT.  The predicate is not a re-implementation of the
  * permission rules: hasResourcePermission is still the single judge, called here once
  * per distinct hotel or activity booking this organization has any conversation about,
  * which is exactly the set of ids canPartnerAccess could ever be asked about.
  */
 public List<MarketplaceConversationResponse> partnerList(UUID a,UUID org,String status,int p,int s){authorization.requireOrganization(org,a);Page r=page(p,s);List<UUID> hotelIds=chatResourcesFor(org,a,"HOTEL",chatMapper.findOrganizationChatHotelIds(org));List<UUID> activityBookingIds=chatResourcesFor(org,a,"ACTIVITY",chatMapper.findOrganizationChatActivityBookingIds(org));return responses(chatMapper.findForOrganizationAccessible(org,a,clean(status),hotelIds,activityBookingIds,r.l,r.o));}
 private List<UUID> chatResourcesFor(UUID org,UUID actor,String resourceType,List<UUID> candidates){return candidates.stream().filter(id->authorization.hasResourcePermission(org,actor,resourceType,id,"CHAT_WRITE")).toList();}
 @Transactional public MarketplaceConversationResponse partnerUpdate(UUID a,UUID id,UpdateConversationStatusRequest r){MarketplaceConversation c=conversation(id,a);if(c.getOrganizationId()==null)throw bad("Direct conversation cannot be assigned by partner");if(!canPartnerAccess(c,a))throw forbidden();if(r.getAssignedMemberId()!=null){OrganizationMember member=orgRepo.findMembers(c.getOrganizationId()).stream().filter(m->r.getAssignedMemberId().equals(m.getId())&&OrganizationMemberStatus.ACTIVE.name().equals(m.getMemberStatus())).findFirst().orElseThrow(()->bad("Assigned employee is not active in this organization"));if(!canPartnerAccess(c,member.getUserId()))throw bad("Assigned employee does not have access to this conversation resource");}repo.updateConversation(id,r.getStatus()==null?c.getStatus():r.getStatus().name(),r.getAssignedMemberId(),r.isUnassign(),LocalDateTime.now());MarketplaceConversation saved=conversation(id,a);history.record(saved.getOrganizationId(),"CONVERSATION",id,"UPDATED",saved,List.of("status","assignedMemberId"),a,"USER",null);return response(saved);}
 public List<MarketplaceConversationResponse> adminList(String q,java.util.List<String> status,java.util.List<String> types,String sort,boolean descending,int p,int s){Page r=page(p,s);return repo.findAdmin(clean(q),status,types,sort,descending,r.l,r.o).stream().map(this::response).toList();}
 public List<MarketplaceMessageResponse> adminMessages(UUID actor,UUID id,Long after,int limit,String reason){MarketplaceConversation conversation=conversation(id,null);String accessReason=clean(reason);if(accessReason==null)throw bad("A reason is required to access a chat transcript");history.audit(conversation.getOrganizationId(),"CONVERSATION",id,"ADMIN_TRANSCRIPT_ACCESSED",actor,"ADMIN",accessReason,Map.of("afterSequence",after==null?0:after,"limit",Math.min(Math.max(limit,1),200)));return rawMessages(id,after,limit);}
 @Transactional public MarketplaceConversationResponse adminUpdate(UUID actor,UUID id,UpdateConversationStatusRequest r){MarketplaceConversation c=conversation(id,null);if(r.getAssignedMemberId()!=null){if(c.getOrganizationId()==null)throw bad("Direct conversation cannot be assigned");orgRepo.findMembers(c.getOrganizationId()).stream().filter(m->r.getAssignedMemberId().equals(m.getId())&&OrganizationMemberStatus.ACTIVE.name().equals(m.getMemberStatus())).findFirst().orElseThrow(()->bad("Assigned employee is not active in this organization"));}repo.updateConversation(id,r.getStatus()==null?c.getStatus():r.getStatus().name(),r.getAssignedMemberId(),r.isUnassign(),LocalDateTime.now());MarketplaceConversation saved=conversation(id,null);history.record(saved.getOrganizationId(),"CONVERSATION",id,"ADMIN_UPDATED",saved,List.of("status","assignedMemberId"),actor,"ADMIN",null);return response(saved);}
 @Transactional public MarketplaceMessageResponse adminSend(UUID actor,UUID id,SendMarketplaceMessageRequest r){MarketplaceConversation c=conversation(id,null);if(c.getOrganizationId()==null)throw bad("Admin cannot join a private direct conversation");return send(actor,id,r);}
 @Transactional public void adminRedact(UUID actor,UUID conversationId,UUID messageId,String reason){MarketplaceConversation c=conversation(conversationId,null);MarketplaceMessage m=repo.findMessage(messageId).orElseThrow(()->notFound("Message not found"));if(!conversationId.equals(m.getConversationId()))throw bad("Message does not belong to conversation");if(repo.softDeleteMessage(conversationId,messageId,LocalDateTime.now())!=1)throw bad("Message is already deleted");history.record(c.getOrganizationId(),"CONVERSATION_MESSAGE",messageId,"ADMIN_REDACTED",m,List.of("content","attachments"),actor,"ADMIN",clean(reason));}
 private List<MarketplaceMessageResponse> rawMessages(UUID id,Long after,int limit){int l=Math.min(Math.max(limit,1),200);return repo.findMessages(id,after,l).stream().map(this::messageResponse).toList();}
 private void require(UUID id,UUID a){MarketplaceConversation c=conversation(id,a);if(c.getOrganizationId()==null){if(!repo.canAccess(id,a))throw forbidden();return;}if(c.getHotelBookingId()!=null){HotelBooking b=hotelRepo.findBooking(c.getHotelBookingId()).orElseThrow(()->notFound("Hotel booking not found"));if(a.equals(b.getUserId()))return;authorization.requireResourcePermission(c.getOrganizationId(),a,"HOTEL",b.getHotelId(),"CHAT_WRITE");return;}if(c.getActivityOrderId()!=null){ActivityOrder o=activityRepo.findOrder(c.getActivityOrderId()).orElseThrow(()->notFound("Activity order not found"));if(a.equals(o.getUserId()))return;authorization.requireResourcePermission(c.getOrganizationId(),a,"ACTIVITY",o.getActivityBookingId(),"CHAT_WRITE");return;}authorization.requirePermission(c.getOrganizationId(),a,"CHAT_WRITE");}private void ensureConversationMember(UUID id,UUID userId){MarketplaceConversation c=conversation(id,userId);if(c.getOrganizationId()==null)return;String role="HOST";if(c.getHotelBookingId()!=null){HotelBooking b=hotelRepo.findBooking(c.getHotelBookingId()).orElseThrow(()->notFound("Hotel booking not found"));if(userId.equals(b.getUserId()))role="GUEST";}else if(c.getActivityOrderId()!=null){ActivityOrder o=activityRepo.findOrder(c.getActivityOrderId()).orElseThrow(()->notFound("Activity order not found"));if(userId.equals(o.getUserId()))role="GUEST";}repo.addMember(id,userId,role,LocalDateTime.now());}private boolean canPartnerAccess(MarketplaceConversation c,UUID a){if(c.getHotelBookingId()!=null){HotelBooking b=hotelRepo.findBooking(c.getHotelBookingId()).orElse(null);return b!=null&&authorization.hasResourcePermission(c.getOrganizationId(),a,"HOTEL",b.getHotelId(),"CHAT_WRITE");}if(c.getActivityOrderId()!=null){ActivityOrder o=activityRepo.findOrder(c.getActivityOrderId()).orElse(null);return o!=null&&authorization.hasResourcePermission(c.getOrganizationId(),a,"ACTIVITY",o.getActivityBookingId(),"CHAT_WRITE");}return true;}private MarketplaceConversation conversation(UUID id,UUID v){return repo.find(id,v).orElseThrow(()->notFound("Conversation not found"));}
 private MarketplaceConversationResponse response(MarketplaceConversation c){return response(c,repo.findParticipants(List.of(c.getId())));}

    /**
     * Maps a page of conversations with one participant query for the whole page rather than
     * one per row: an inbox is the screen most likely to be opened on a slow connection, and
     * N+1 there is felt immediately.
     */
    private List<MarketplaceConversationResponse> responses(List<MarketplaceConversation> conversations) {
        if (conversations.isEmpty()) return List.of();
        List<MarketplaceConversationParticipant> participants =
                repo.findParticipants(conversations.stream().map(MarketplaceConversation::getId).toList());
        return conversations.stream().map(conversation -> response(conversation, participants)).toList();
    }

    private MarketplaceConversationResponse response(MarketplaceConversation c, List<MarketplaceConversationParticipant> allParticipants) {
        List<MarketplaceConversationResponse.ConversationParticipantResponse> participants = allParticipants.stream()
                .filter(participant -> c.getId().equals(participant.getConversationId()))
                .map(participant -> MarketplaceConversationResponse.ConversationParticipantResponse.builder()
                        .userId(participant.getUserId())
                        .fullName(participant.getFullName())
                        .username(participant.getUsername())
                        .avatarUrl(participant.getAvatarUrl())
                        .memberRole(participant.getMemberRole())
                        .build())
                .toList();
        return MarketplaceConversationResponse.builder().id(c.getId()).conversationType(c.getConversationType()).organizationId(c.getOrganizationId()).hotelBookingId(c.getHotelBookingId()).activityOrderId(c.getActivityOrderId()).status(c.getStatus()).assignedMemberId(c.getAssignedMemberId()).organizationName(c.getOrganizationName()).bookingCode(c.getBookingCode()).orderCode(c.getOrderCode()).lastMessageContent(c.getLastMessageContent()).unreadCount(c.getUnreadCount()).lastMessageAt(c.getLastMessageAt()).createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt())
                .participants(participants)
                .title(title(c, participants))
                .build();
    }

    /**
     * Decided here so every client heads the same thread the same way. A booking names itself,
     * a business enquiry names the business, and a person-to-person thread can only be the
     * people in it — viewer-independent on purpose, since the title is cached in an inbox row.
     */
    private String title(MarketplaceConversation c, List<MarketplaceConversationResponse.ConversationParticipantResponse> participants) {
        if (c.getBookingCode() != null) return c.getOrganizationName() == null ? c.getBookingCode() : c.getOrganizationName();
        if (c.getOrderCode() != null) return c.getOrganizationName() == null ? c.getOrderCode() : c.getOrganizationName();
        if (c.getOrganizationName() != null) return c.getOrganizationName();
        return participants.stream()
                .map(participant -> participant.getFullName() == null ? participant.getUsername() : participant.getFullName())
                .filter(Objects::nonNull)
                .limit(3)
                .collect(java.util.stream.Collectors.joining(", "));
    }
 private MarketplaceMessageResponse messageResponse(MarketplaceMessage m){return MarketplaceMessageResponse.builder().id(m.getId()).conversationId(m.getConversationId()).senderUserId(m.getSenderUserId()).senderName(m.getSenderName()).senderAvatarUrl(m.getSenderAvatarUrl()).clientMessageId(m.getClientMessageId()).messageType(m.getMessageType()).content(m.getDeletedAt()==null?m.getContent():null).attachments(m.getDeletedAt()==null?readAttachments(m.getAttachments()):List.of()).sequenceNo(m.getSequenceNo()).editedAt(m.getEditedAt()).deletedAt(m.getDeletedAt()).createdAt(m.getCreatedAt()).build();}
 private void broadcastAfterCommit(UUID conversationId,UUID actor,MarketplaceMessageResponse response){Map<String,Object> data=mapper.convertValue(response,new TypeReference<Map<String,Object>>(){});if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()){org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){webSocketService.broadcastToConversation(conversationId,"MESSAGE_CREATED",data,actor);}});}else webSocketService.broadcastToConversation(conversationId,"MESSAGE_CREATED",data,actor);}private String json(Object v){try{return mapper.writeValueAsString(v);}catch(JsonProcessingException e){throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR,"Cannot serialize chat data");}}private List<Map<String,Object>> readAttachments(String s){if(s==null)return List.of();try{return mapper.readValue(s,new TypeReference<List<Map<String,Object>>>(){});}catch(Exception e){return List.of();}}private Page page(int p,int s){int l=Math.min(Math.max(s,1),200);return new Page(l,Math.max(p,0)*l);}private String clean(String s){return s==null||s.isBlank()?null:s.trim();}private BusinessException bad(String m){return new BusinessException(ErrorConstant.BAD_REQUEST,m);}private BusinessException notFound(String m){return new BusinessException(ErrorConstant.NOT_FOUND,m);}private BusinessException forbidden(){return new BusinessException(ErrorConstant.FORBIDDEN_ERROR,"You cannot access this conversation");}private record Page(int l,int o){}

    /**
     * A person-to-person thread, either with another traveller or with a business before any
     * booking exists.
     *
     * <p>Reuses the existing thread when one is already open between exactly these people.
     * Without that, every tap on Message would start a fresh conversation and the reply would
     * land in a thread the other side has not opened — which is how a chat feature looks broken
     * while working exactly as written.
     */
    private MarketplaceConversationResponse startDirect(UUID actor, StartMarketplaceConversationRequest request) {
        if (!businessConfig.getBoolean(com.ds.goroute.type.BusinessConfigKey.CHAT_DIRECT_ENABLED)) {
            throw new BusinessException(ErrorConstant.CHAT_DIRECT_DISABLED);
        }
        UUID organizationId = request.getOrganizationId();
        List<UUID> members = organizationId == null
                ? directPeople(actor, request.getParticipantUserIds())
                : businessContacts(actor, organizationId);

        // Only person-to-person. A thread addressed to a business is answered by whoever
        // is on duty, and one blocked colleague must not close the business's inbox.
        if (organizationId == null) {
            java.util.Set<UUID> blocked = userBlocks.blockedAmong(actor, members);
            if (!blocked.isEmpty()) {
                throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                        "You cannot start a conversation with this person");
            }
        }

        MarketplaceConversation existing = repo.findDirectByMembers(members, organizationId).orElse(null);
        if (existing != null) {
            require(existing.getId(), actor);
            return response(repo.find(existing.getId(), actor).orElse(existing));
        }
        MarketplaceConversation created = create(MarketplaceConversationType.DIRECT, organizationId, null, null);
        for (UUID member : members) {
            repo.addMember(created.getId(), member, directRole(member, actor, organizationId), created.getCreatedAt());
        }
        history.record(organizationId, "CONVERSATION", created.getId(), "CREATED", created, List.of(), actor, "USER", null);
        return response(repo.find(created.getId(), actor).orElse(created));
    }

    /**
     * Refuses a message into a direct thread where somebody has since blocked somebody.
     *
     * <p>Checked on send and not only on start, because a block made after a thread was
     * opened has to stop the next message; a thread that already exists is exactly the case
     * a block is usually made about.
     *
     * <p>Booking and order threads are exempt: the commercial relationship outlives the
     * personal one.
     */
    private void ensureNotBlocked(UUID conversationId, UUID sender) {
        MarketplaceConversation conversation = repo.find(conversationId, sender).orElse(null);
        if (conversation == null || !MarketplaceConversationType.DIRECT.name().equals(conversation.getConversationType())) {
            return;
        }
        if (conversation.getOrganizationId() != null) return;

        List<UUID> others = repo.findParticipants(List.of(conversationId)).stream()
                .map(com.ds.goroute.entity.MarketplaceConversationParticipant::getUserId)
                .filter(member -> member != null && !member.equals(sender))
                .toList();
        if (userBlocks.blockedAmong(sender, others).isEmpty()) return;
        throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                "You cannot send messages in this conversation");
    }

    private List<UUID> directPeople(UUID actor, List<UUID> requested) {
        List<UUID> members = new ArrayList<>(requested == null ? List.of() : requested);
        members.removeIf(Objects::isNull);
        if (members.stream().noneMatch(actor::equals)) members.add(actor);
        members = members.stream().distinct().toList();
        if (members.size() < 2 || members.size() > 20) throw bad("Direct conversation requires 2-20 participants");
        for (UUID member : members) {
            userRepo.findById(member).orElseThrow(() -> new BusinessException(ErrorConstant.USER_NOT_FOUND));
        }
        return members;
    }

    /**
     * Messaging a business, not a person: the guest plus whoever actually answers for it.
     *
     * <p>Resolved through {@code notificationRecipients} rather than by listing members, because
     * the owner is never a member row and a business with no staff would otherwise open a thread
     * nobody is in.
     */
    private List<UUID> businessContacts(UUID actor, UUID organizationId) {
        HostOrganization organization = orgRepo.findById(organizationId)
                .orElseThrow(() -> notFound("Partner organization not found"));
        boolean open = com.ds.goroute.type.OrganizationVerificationStatus.VERIFIED.name().equals(organization.getVerificationStatus())
                && com.ds.goroute.type.OrganizationOperationalStatus.ENABLED.name().equals(organization.getOperationalStatus());
        if (!open) throw notFound("Partner organization not found");
        List<UUID> hosts = authorization.notificationRecipients(organizationId, null, null, "CHAT_WRITE", actor);
        if (hosts.isEmpty()) throw bad("This business cannot receive messages yet");
        List<UUID> members = new ArrayList<>();
        members.add(actor);
        members.addAll(hosts);
        return members.stream().distinct().toList();
    }

    private String directRole(UUID member, UUID actor, UUID organizationId) {
        if (organizationId == null) return member.equals(actor) ? "CREATOR" : "PARTICIPANT";
        return member.equals(actor) ? "GUEST" : "HOST";
    }
}
