package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** What Pengoo keeps about the caller across their 1:1 chats, one short fact per item. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantProfileResponse {
    private List<String> items;
}
