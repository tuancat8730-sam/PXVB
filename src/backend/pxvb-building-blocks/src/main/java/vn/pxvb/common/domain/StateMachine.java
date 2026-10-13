package vn.pxvb.common.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;

/**
 * Máy trạng thái bất biến: định nghĩa các chuyển trạng thái hợp lệ, {@link #transition} trả trạng thái mới
 * hoặc ném {@link BusinessException} nếu không hợp lệ.
 */
public final class StateMachine<S extends Enum<S>> {

    private final Map<S, Set<S>> allowed;

    private StateMachine(Map<S, Set<S>> allowed) {
        this.allowed = allowed;
    }

    public static <S extends Enum<S>> Builder<S> builder() {
        return new Builder<>();
    }

    public boolean canTransition(S from, S to) {
        return allowed.getOrDefault(from, Set.of()).contains(to);
    }

    public S transition(S from, S to) {
        if (!canTransition(from, to)) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Không thể chuyển trạng thái từ " + from + " sang " + to);
        }
        return to;
    }

    public static final class Builder<S extends Enum<S>> {
        private final Map<S, Set<S>> map = new HashMap<>();

        public Builder<S> allow(S from, S to) {
            map.computeIfAbsent(from, k -> new HashSet<>()).add(to);
            return this;
        }

        public StateMachine<S> build() {
            Map<S, Set<S>> copy = new HashMap<>();
            map.forEach((k, v) -> copy.put(k, Set.copyOf(v)));
            return new StateMachine<>(Map.copyOf(copy));
        }
    }
}
