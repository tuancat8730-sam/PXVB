package vn.pxvb.masterdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Bất biến cho đồng bộ (L30): tài nguyên tablet kéo về mà có thể xóa cứng thì phải luôn trả đầy đủ (alwaysFull).
 * Nếu sau này thêm đường xóa cứng cho tài nguyên delta, cần thiết kế tombstone trước khi bỏ test này.
 */
class MasterdataPullDeletionInvariantTest {

    @Test
    void hardDeletableResourcesArePulledAsFullSnapshots() {
        // Arrange
        MasterdataPullProvider provider = new MasterdataPullProvider(mock(CatalogService.class));

        // Act
        List<String> offenders = provider.resources().stream()
                .filter(resource -> !provider.alwaysFull().contains(resource))
                .filter(resource -> Catalogs.byResource(resource).hardDeletable())
                .toList();

        // Assert
        assertThat(offenders).isEmpty();
    }
}
