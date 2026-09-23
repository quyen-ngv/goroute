package com.ds.goroute.partneronboarding;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateHostOrganizationRequest;
import com.ds.goroute.dto.response.HostOrganizationResponse;
import com.ds.goroute.entity.HostOrganization;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.partneronboarding.domain.DraftStatus;
import com.ds.goroute.partneronboarding.domain.ListingKind;
import com.ds.goroute.partneronboarding.domain.OnboardingDraft;
import com.ds.goroute.partneronboarding.domain.OnboardingSteps;
import com.ds.goroute.partneronboarding.dto.CreateDraftRequest;
import com.ds.goroute.partneronboarding.dto.SaveStepRequest;
import com.ds.goroute.partneronboarding.dto.SubmitResultResponse;
import com.ds.goroute.partneronboarding.persistence.OnboardingDraftRepository;
import com.ds.goroute.partneronboarding.submit.ListingMaterializer;
import com.ds.goroute.partneronboarding.submit.ListingMaterializers;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.HostOrganizationService;
import com.ds.goroute.service.MarketplaceHistoryService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.ds.goroute.type.BusinessConfigKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PartnerOnboardingServiceImpl")
class PartnerOnboardingServiceImplTest {

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID COLLEAGUE = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final UUID ORGANIZATION = UUID.randomUUID();
    private static final UUID DRAFT_ID = UUID.randomUUID();
    private static final UUID HOTEL_ID = UUID.randomUUID();

    private OnboardingDraftRepository drafts;
    private HostOrganizationService organizations;
    private PartnerAuthorizationService authorization;
    private BusinessConfigService config;
    private BetaAccessService betaAccess;
    private ListingMaterializers materializers;
    private ListingMaterializer stayMaterializer;
    private PartnerOnboardingServiceImpl service;

    @BeforeEach
    void setUp() {
        drafts = mock(OnboardingDraftRepository.class);
        organizations = mock(HostOrganizationService.class);
        authorization = mock(PartnerAuthorizationService.class);
        config = mock(BusinessConfigService.class);
        betaAccess = mock(BetaAccessService.class);
        materializers = mock(ListingMaterializers.class);
        stayMaterializer = mock(ListingMaterializer.class);

        when(config.getBoolean(BusinessConfigKey.PARTNER_ONBOARDING_ENABLED)).thenReturn(true);
        when(config.getBoolean(BusinessConfigKey.PARTNER_APP_ENABLED)).thenReturn(true);
        when(config.getInt(BusinessConfigKey.PARTNER_ONBOARDING_MAX_DRAFTS)).thenReturn(5);
        when(materializers.of(any())).thenReturn(stayMaterializer);
        when(drafts.updateStep(any(), anyLong(), any(), any(), any(), any())).thenReturn(true);
        when(drafts.updateOrganization(any(), anyLong(), any(), any())).thenReturn(true);
        when(drafts.updateStatus(any(), anyLong(), any(), any(), any(), any(), any())).thenReturn(true);

        ObjectMapper objectMapper = new ObjectMapper();
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            service = new PartnerOnboardingServiceImpl(drafts, organizations, authorization, config, betaAccess,
                    mock(FileUploadService.class), mock(MarketplaceHistoryService.class), materializers,
                    new MarketplaceJson(objectMapper), objectMapper, validator);
        }
    }

    @Nested
    @DisplayName("access")
    class Access {

        @Test
        @DisplayName("lets the author open their own draft")
        void authorCanRead() {
            given(draft(DraftStatus.DRAFT));

            assertThat(service.get(AUTHOR, DRAFT_ID).getId()).isEqualTo(DRAFT_ID);
        }

        @Test
        @DisplayName("lets a colleague who could list for the same business open it")
        void colleagueWithWritePermissionCanRead() {
            given(draft(DraftStatus.DRAFT));
            when(authorization.hasPermission(ORGANIZATION, COLLEAGUE, "HOTEL_WRITE")).thenReturn(true);

            assertThat(service.get(COLLEAGUE, DRAFT_ID).getId()).isEqualTo(DRAFT_ID);
        }

        @Test
        @DisplayName("answers a stranger with not-found, so a guessed id confirms nothing")
        void strangerGetsNotFoundRatherThanForbidden() {
            given(draft(DraftStatus.DRAFT));
            when(authorization.hasPermission(ORGANIZATION, STRANGER, "HOTEL_WRITE")).thenReturn(false);

            assertThatThrownBy(() -> service.get(STRANGER, DRAFT_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getError().getCode())
                    .isEqualTo(ErrorConstant.ONBOARDING_DRAFT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("saving a step")
    class SaveStep {

        @Test
        @DisplayName("stores the answers under the step code and marks it complete")
        void storesAnswers() {
            given(draft(DraftStatus.DRAFT));

            service.saveStep(AUTHOR, DRAFT_ID, OnboardingSteps.STAY_BASICS, step(Map.of("guests", 4)));

            ArgumentCaptor<String> data = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> completed = ArgumentCaptor.forClass(String.class);
            verify(drafts).updateStep(eq(DRAFT_ID), eq(3L), eq(OnboardingSteps.STAY_BASICS),
                    completed.capture(), data.capture(), any());
            assertThat(data.getValue()).contains("\"guests\":4");
            assertThat(completed.getValue()).contains(OnboardingSteps.STAY_BASICS);
        }

        @Test
        @DisplayName("refuses a step code nothing will ever read")
        void refusesAnUnknownStep() {
            assertThatThrownBy(() -> service.saveStep(AUTHOR, DRAFT_ID, "stay.colour", step(Map.of())))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getError().getCode())
                    .isEqualTo(ErrorConstant.ONBOARDING_STEP_UNKNOWN);
            verify(drafts, never()).updateStep(any(), anyLong(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("reports a conflict when the draft moved under a concurrent write")
        void reportsAConflict() {
            given(draft(DraftStatus.DRAFT));
            when(drafts.updateStep(any(), anyLong(), any(), any(), any(), any())).thenReturn(false);

            assertThatThrownBy(() -> service.saveStep(AUTHOR, DRAFT_ID, OnboardingSteps.STAY_BASICS, step(Map.of())))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("changed somewhere else");
        }

        @Test
        @DisplayName("creates the business the organization step described, and attaches it")
        void createsTheOrganization() {
            OnboardingDraft withoutOrganization = draft(DraftStatus.DRAFT);
            withoutOrganization.setOrganizationId(null);
            given(withoutOrganization);
            when(organizations.create(eq(AUTHOR), any()))
                    .thenReturn(HostOrganizationResponse.builder().id(ORGANIZATION).build());

            service.saveStep(AUTHOR, DRAFT_ID, OnboardingSteps.ORGANIZATION, step(Map.of(
                    "legalName", "Lotus Hospitality JSC",
                    "displayName", "Lotus Loft",
                    "contactPhone", "+84 90 000 0000",
                    "timezone", "Asia/Ho_Chi_Minh")));

            ArgumentCaptor<CreateHostOrganizationRequest> captor =
                    ArgumentCaptor.forClass(CreateHostOrganizationRequest.class);
            verify(organizations).create(eq(AUTHOR), captor.capture());
            assertThat(captor.getValue().getDisplayName()).isEqualTo("Lotus Loft");
            verify(drafts).updateOrganization(eq(DRAFT_ID), eq(3L), eq(ORGANIZATION), any());
            // The organization write moved the version on, so the answers must use the next one.
            verify(drafts).updateStep(eq(DRAFT_ID), eq(4L), any(), any(), any(), any());
        }

        @Test
        @DisplayName("applies the business rules even though the form arrives as a loose map")
        void validatesTheOrganizationForm() {
            OnboardingDraft withoutOrganization = draft(DraftStatus.DRAFT);
            withoutOrganization.setOrganizationId(null);
            given(withoutOrganization);

            assertThatThrownBy(() -> service.saveStep(AUTHOR, DRAFT_ID, OnboardingSteps.ORGANIZATION,
                    step(Map.of("legalName", "Lotus Hospitality JSC", "displayName", "Lotus Loft"))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("contactPhone");
            verify(organizations, never()).create(any(), any());
        }

        @Test
        @DisplayName("checks the caller may list for a business they picked instead of created")
        void checksPermissionOnAChosenOrganization() {
            OnboardingDraft withoutOrganization = draft(DraftStatus.DRAFT);
            withoutOrganization.setOrganizationId(null);
            given(withoutOrganization);
            when(authorization.requirePermission(eq(ORGANIZATION), eq(AUTHOR), eq("HOTEL_WRITE")))
                    .thenThrow(new BusinessException(ErrorConstant.FORBIDDEN_ERROR));

            assertThatThrownBy(() -> service.saveStep(AUTHOR, DRAFT_ID, OnboardingSteps.ORGANIZATION,
                    step(Map.of("organizationId", ORGANIZATION.toString()))))
                    .isInstanceOf(BusinessException.class);
            verify(organizations, never()).create(any(), any());
        }
    }

    @Nested
    @DisplayName("submitting")
    class Submit {

        @Test
        @DisplayName("creates the listing and records what the draft produced")
        void createsTheListing() {
            given(draft(DraftStatus.DRAFT));
            when(authorization.requirePermission(ORGANIZATION, AUTHOR, "HOTEL_WRITE")).thenReturn(organization());
            when(stayMaterializer.materialize(any())).thenReturn(SubmitResultResponse.builder()
                    .draftId(DRAFT_ID).listingKind(ListingKind.STAY).hotelId(HOTEL_ID).build());

            SubmitResultResponse result = service.submit(AUTHOR, DRAFT_ID, 3L);

            assertThat(result.getHotelId()).isEqualTo(HOTEL_ID);
            verify(drafts).updateStatus(eq(DRAFT_ID), eq(3L), eq(DraftStatus.SUBMITTED),
                    eq(HOTEL_ID), eq(null), any(), any());
        }

        @Test
        @DisplayName("passes the organization's own clock and currency to the materializer")
        void passesOrganizationDefaults() {
            given(draft(DraftStatus.DRAFT));
            when(authorization.requirePermission(ORGANIZATION, AUTHOR, "HOTEL_WRITE")).thenReturn(organization());
            when(stayMaterializer.materialize(any())).thenReturn(SubmitResultResponse.builder()
                    .draftId(DRAFT_ID).listingKind(ListingKind.STAY).hotelId(HOTEL_ID).build());

            service.submit(AUTHOR, DRAFT_ID, null);

            ArgumentCaptor<ListingMaterializer.Context> captor =
                    ArgumentCaptor.forClass(ListingMaterializer.Context.class);
            verify(stayMaterializer).materialize(captor.capture());
            assertThat(captor.getValue().organizationTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
            assertThat(captor.getValue().defaultCurrency()).isEqualTo("VND");
            assertThat(captor.getValue().data().step(OnboardingSteps.STAY_TITLE).text("title"))
                    .isEqualTo("Cosy loft");
        }

        @Test
        @DisplayName("refuses a draft with no business behind it yet")
        void refusesWithoutAnOrganization() {
            OnboardingDraft withoutOrganization = draft(DraftStatus.DRAFT);
            withoutOrganization.setOrganizationId(null);
            given(withoutOrganization);

            assertThatThrownBy(() -> service.submit(AUTHOR, DRAFT_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getError().getCode())
                    .isEqualTo(ErrorConstant.ONBOARDING_ORGANIZATION_REQUIRED);
        }

        @Test
        @DisplayName("tells a retrying client what already exists rather than building a second listing")
        void refusesToSubmitTwice() {
            OnboardingDraft submitted = draft(DraftStatus.SUBMITTED);
            submitted.setResultHotelId(HOTEL_ID);
            given(submitted);

            assertThatThrownBy(() -> service.submit(AUTHOR, DRAFT_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(HOTEL_ID.toString());
            verify(stayMaterializer, never()).materialize(any());
        }

        @Test
        @DisplayName("leaves the draft editable when the materializer fails")
        void leavesTheDraftEditableOnFailure() {
            given(draft(DraftStatus.DRAFT));
            when(authorization.requirePermission(ORGANIZATION, AUTHOR, "HOTEL_WRITE")).thenReturn(organization());
            when(stayMaterializer.materialize(any()))
                    .thenThrow(new BusinessException(ErrorConstant.ONBOARDING_INCOMPLETE, "missing title"));

            assertThatThrownBy(() -> service.submit(AUTHOR, DRAFT_ID, null))
                    .isInstanceOf(BusinessException.class);
            verify(drafts, never()).updateStatus(any(), anyLong(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("creating")
    class Create {

        @Test
        @DisplayName("refuses another draft once the account is holding its limit")
        void enforcesTheDraftLimit() {
            when(drafts.countByUser(AUTHOR, DraftStatus.DRAFT)).thenReturn(5L);

            CreateDraftRequest request = new CreateDraftRequest();
            request.setListingKind(ListingKind.STAY);

            assertThatThrownBy(() -> service.create(AUTHOR, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getError().getCode())
                    .isEqualTo(ErrorConstant.ONBOARDING_TOO_MANY_DRAFTS);
            verify(drafts, never()).insert(any());
        }

        @Test
        @DisplayName("refuses to start when the wizard is switched off")
        void respectsTheFeatureSwitch() {
            when(config.getBoolean(BusinessConfigKey.PARTNER_ONBOARDING_ENABLED)).thenReturn(false);

            CreateDraftRequest request = new CreateDraftRequest();
            request.setListingKind(ListingKind.STAY);

            assertThatThrownBy(() -> service.create(AUTHOR, request))
                    .isInstanceOf(BusinessException.class);
            verify(drafts, never()).insert(any());
        }

        @Test
        @DisplayName("lets a beta tester start while the wizard is still switched off")
        void betaTestersSeeItFirst() {
            when(config.getBoolean(BusinessConfigKey.PARTNER_ONBOARDING_ENABLED)).thenReturn(false);
            when(betaAccess.isBetaUser(AUTHOR)).thenReturn(true);

            CreateDraftRequest request = new CreateDraftRequest();
            request.setListingKind(ListingKind.STAY);

            service.create(AUTHOR, request);

            verify(drafts).insert(any());
        }
    }

    @Test
    @DisplayName("reports the feature switches and the drafts an account is holding")
    void meReportsTheEntryState() {
        when(organizations.listMine(AUTHOR)).thenReturn(List.of(HostOrganizationResponse.builder()
                .id(ORGANIZATION).displayName("Lotus Loft").ownerUserId(AUTHOR)
                .verificationStatus("UNVERIFIED").operationalStatus("ENABLED").build()));
        when(drafts.findByUser(eq(AUTHOR), eq(DraftStatus.DRAFT), anyInt(), anyInt()))
                .thenReturn(List.of(draft(DraftStatus.DRAFT)));

        var me = service.me(AUTHOR);

        assertThat(me.isEnabled()).isTrue();
        assertThat(me.isPartnerAppEnabled()).isTrue();
        assertThat(me.getOrganizations()).singleElement()
                .satisfies(organization -> assertThat(organization.isOwner()).isTrue());
        assertThat(me.getDrafts()).singleElement()
                .satisfies(summary -> assertThat(summary.getCompletedStepCount()).isEqualTo(1));
    }

    // --- fixtures -----------------------------------------------------------------------

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    private void given(OnboardingDraft draft) {
        when(drafts.findById(DRAFT_ID)).thenReturn(Optional.of(draft));
    }

    private OnboardingDraft draft(DraftStatus status) {
        return OnboardingDraft.builder()
                .id(DRAFT_ID)
                .userId(AUTHOR)
                .organizationId(ORGANIZATION)
                .listingKind(ListingKind.STAY.name())
                .status(status.name())
                .completedSteps("[\"" + OnboardingSteps.STAY_TITLE + "\"]")
                .data("{\"" + OnboardingSteps.STAY_TITLE + "\":{\"title\":\"Cosy loft\"}}")
                .dataVersion(3L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private HostOrganization organization() {
        return HostOrganization.builder()
                .id(ORGANIZATION)
                .ownerUserId(AUTHOR)
                .displayName("Lotus Loft")
                .timezone("Asia/Ho_Chi_Minh")
                .defaultCurrency("VND")
                .build();
    }

    private SaveStepRequest step(Map<String, Object> data) {
        SaveStepRequest request = new SaveStepRequest();
        request.setData(data);
        return request;
    }
}
