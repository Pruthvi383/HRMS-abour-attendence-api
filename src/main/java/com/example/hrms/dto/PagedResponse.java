package com.example.hrms.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PagedResponse<T> {

    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int currentPage;

    public static <T> PagedResponse<T> of(List<T> content, long totalElements, int totalPages, int currentPage) {
        return PagedResponse.<T>builder()
            .content(content)
            .totalElements(totalElements)
            .totalPages(totalPages)
            .currentPage(currentPage)
            .build();
    }
}
