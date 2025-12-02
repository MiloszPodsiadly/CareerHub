package com.milosz.podsiadly.careerhub.agentcrawler.nfj.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NfjSearchRequest {

    private String criteria;
    private Url url;
    private String rawSearch;
    private int pageSize;
    private boolean withSalaryMatch;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Url {
        private String searchParam;
    }

    public static NfjSearchRequest forCategorySlug(String slug, int pageSize) {
        return new NfjSearchRequest(
                "",
                new Url(slug),
                slug,
                pageSize,
                false
        );
    }
}
