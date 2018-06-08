package se.redsharp.politescraper;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.*;

final class DefaultTimeProviderTest {

    private static final long SLEEP_MS = 100L;
    private static final long MAX_TIME_FOR_TEST = 30;
    private final DefaultTimeProvider provider = new DefaultTimeProvider();

    @Test
    void getsCurrentTime() {
        assertThat(System.currentTimeMillis() - provider.currentTimeMillis(), is(lessThan(MAX_TIME_FOR_TEST)));
    }

    @Test
    void sleeps() throws InterruptedException {
        long beforeTime = System.currentTimeMillis();
        provider.sleep(SLEEP_MS);
        assertThat(System.currentTimeMillis() - beforeTime, is(lessThan(SLEEP_MS + MAX_TIME_FOR_TEST)));
    }

}