package dev.bee.kanjianki.data;

import java.lang.reflect.Proxy;
import java.util.Collections;
import kotlin.coroutines.Continuation;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class HomeRepositoryJavaCompatibilityTest {
    @Test
    public void defaultSearchArgumentRemainsAvailableThroughCompatibilityBridge()
        throws ReflectiveOperationException {
        HomeRepository repository = (HomeRepository) Proxy.newProxyInstance(
            HomeRepository.class.getClassLoader(),
            new Class<?>[] {HomeRepository.class},
            (proxy, method, arguments) -> {
                if ("searchInventory".equals(method.getName())) {
                    return StoreResult.ok(Collections.emptyList());
                }
                throw new UnsupportedOperationException(method.getName());
            }
        );

        Object result = HomeRepository.DefaultImpls.class
            .getDeclaredMethod(
                "searchInventory$default",
                HomeRepository.class,
                String.class,
                boolean.class,
                Continuation.class,
                int.class,
                Object.class
            )
            .invoke(
                null,
                repository,
                "rest",
                true,
                null,
                2,
                null
            );

        assertTrue(((StoreResult<?>) result).isOk());
    }
}
