package vn.pxvb.common.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Phân trang chuẩn {@code ?page=&pageSize=} (SDD §2.5); page bắt đầu từ 0. */
public record PageResponse<T>(List<T> content, int page, int pageSize, long total) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
