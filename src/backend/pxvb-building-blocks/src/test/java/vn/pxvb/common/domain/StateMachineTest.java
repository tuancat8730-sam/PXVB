package vn.pxvb.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;

class StateMachineTest {

    enum S { NEW, OPEN, DONE }

    private final StateMachine<S> machine = StateMachine.<S>builder()
            .allow(S.NEW, S.OPEN)
            .allow(S.OPEN, S.DONE)
            .build();

    @Test
    void allowsDeclaredTransition() {
        assertThat(machine.transition(S.NEW, S.OPEN)).isEqualTo(S.OPEN);
        assertThat(machine.canTransition(S.OPEN, S.DONE)).isTrue();
    }

    @Test
    void rejectsUndeclaredTransition() {
        assertThat(machine.canTransition(S.DONE, S.NEW)).isFalse();
        assertThatThrownBy(() -> machine.transition(S.NEW, S.DONE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }
}
