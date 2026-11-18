package eu.kalafatic.dstudio.core.event;

import java.util.function.Consumer;

public interface EventBus {

    <T> void publish(T event);

    <T> AutoCloseable subscribe(Class<T> eventType, Consumer<T> listener);
}
