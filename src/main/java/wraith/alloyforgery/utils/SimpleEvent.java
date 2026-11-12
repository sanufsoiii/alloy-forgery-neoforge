package wraith.alloyforgery.utils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Minimal replacement for Fabric API's {@code Event}/{@code EventFactory.createArrayBacked}:
 * listeners are kept in registration order and called through {@link #invoker()}.
 */
public final class SimpleEvent<T> {

    private final List<T> listeners = new CopyOnWriteArrayList<>();
    private final T invoker;

    private SimpleEvent(Function<List<T>, T> invokerFactory) {
        this.invoker = invokerFactory.apply(this.listeners);
    }

    public static <T> SimpleEvent<T> create(Function<List<T>, T> invokerFactory) {
        return new SimpleEvent<>(invokerFactory);
    }

    public void register(T listener) {
        this.listeners.add(listener);
    }

    public T invoker() {
        return this.invoker;
    }
}
