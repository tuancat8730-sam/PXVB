package vn.pxvb.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.pxvb.common.security.DataScope.Type;

class DataScopeTest {

    @Test
    void allScopePermitsEverything() {
        assertThat(DataScope.permits(List.of(DataScope.all()), Type.LINE, "L01")).isTrue();
    }

    @Test
    void lineScopePermitsOnlyMatchingLine() {
        List<DataScope> scopes = List.of(new DataScope(Type.LINE, "L01"));
        assertThat(DataScope.permits(scopes, Type.LINE, "L01")).isTrue();
        assertThat(DataScope.permits(scopes, Type.LINE, "L02")).isFalse();
        assertThat(DataScope.permits(scopes, Type.UNIT, "L01")).isFalse();
    }

    @Test
    void emptyScopesPermitNothing() {
        assertThat(DataScope.permits(List.of(), Type.LINE, "L01")).isFalse();
    }
}
