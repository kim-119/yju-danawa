package yju.danawa.com.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AladinLookupResponse(List<AladinLookupItem> item) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AladinLookupItem(
            String title,
            String author,
            String publisher,
            String pubDate,
            String description,
            String isbn,
            String isbn13,
            String cover,
            String categoryName,
            String link,
            Integer priceStandard,
            Integer priceSales,
            Integer customerReviewRank,
            Integer bestRank,
            SubInfo subInfo
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubInfo(
            String subTitle,
            String originalTitle,
            Integer itemPage,
            String toc,
            Object packing
    ) {}
}

