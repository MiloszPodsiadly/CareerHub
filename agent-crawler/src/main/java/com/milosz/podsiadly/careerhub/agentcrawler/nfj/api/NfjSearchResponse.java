package com.milosz.podsiadly.careerhub.agentcrawler.nfj.api;

import lombok.Data;

import java.util.List;

@Data
public class NfjSearchResponse {

    private List<Posting> postings;
    private int totalCount;
    private int totalPages;

    @Data
    public static class Posting {
        private String id;
        private String url;
    }
}
