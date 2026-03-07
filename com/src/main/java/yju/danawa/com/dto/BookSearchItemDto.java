package yju.danawa.com.dto;

import java.time.LocalDate;
import java.util.List;

public record BookSearchItemDto(
        String isbn,
        String title,
        String titleNorm,
        String author,
        String publisher,
        LocalDate publishedDate,
        List<String> imageUrls) {
}
