package se.redsharp.politescraper;

/**
 * Simple facade over the Systems own time providing methods.
 */
public final class DefaultTimeProvider implements TimeProvider {

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}