package org.bike.fx;

public interface SectionController {
    void bind(FxContext context);

    default boolean canLeave() {
        return true;
    }

    default void refresh() {}

    default void dispose() {}
}
