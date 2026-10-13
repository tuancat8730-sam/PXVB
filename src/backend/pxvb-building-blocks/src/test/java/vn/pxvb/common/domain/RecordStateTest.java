package vn.pxvb.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RecordStateTest {

    @Test
    void draftAndSubmittedAreEditable() {
        assertThat(RecordState.DRAFT.isLocked()).isFalse();
        assertThat(RecordState.SUBMITTED.isLocked()).isFalse();
    }

    @Test
    void confirmedAndBeyondAreLocked() {
        assertThat(RecordState.CONFIRMED.isLocked()).isTrue();
        assertThat(RecordState.SHIFT_LOCKED.isLocked()).isTrue();
        assertThat(RecordState.FINALIZED.isLocked()).isTrue();
    }
}
