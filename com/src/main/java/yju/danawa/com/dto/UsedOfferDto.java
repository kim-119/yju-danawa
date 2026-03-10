package yju.danawa.com.dto;

public record UsedOfferDto(
        Long id,
        String sellerUsername,
        String bookCondition,
        Integer priceWon
) {}
