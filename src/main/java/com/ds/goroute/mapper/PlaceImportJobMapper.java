package com.ds.goroute.mapper;

import com.ds.goroute.entity.PlaceImportJob;
import com.ds.goroute.entity.PlaceImportJobItem;
import com.ds.goroute.dto.response.AdminPlaceImportMappingResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PlaceImportJobMapper {
    void insertJob(PlaceImportJob job);

    void updateJob(PlaceImportJob job);

    PlaceImportJob findJobById(@Param("id") UUID id);

    List<PlaceImportJob> findJobsByUserId(@Param("userId") UUID userId,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);

    List<PlaceImportJob> findAdminJobs(@Param("userId") UUID userId,
                                       @Param("status") String status,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    void insertItem(PlaceImportJobItem item);

    void updateItem(PlaceImportJobItem item);

    PlaceImportJobItem findItemById(@Param("id") UUID id);

    boolean existsActivityItem(@Param("activityId") UUID activityId);

    boolean existsSocialItem(@Param("socialJobId") UUID socialJobId,
                             @Param("sourceCandidateKey") String sourceCandidateKey);

    List<PlaceImportJobItem> findSocialItemsBySocialJobId(@Param("socialJobId") UUID socialJobId);

    List<AdminPlaceImportMappingResponse> findAdminMappings(
            @Param("approvalStatus") String approvalStatus,
            @Param("limit") int limit,
            @Param("offset") int offset);

    AdminPlaceImportMappingResponse findAdminMappingByItemId(@Param("itemId") UUID itemId);

    List<PlaceImportJobItem> findItemsByJobId(@Param("jobId") UUID jobId);
}
