package se.redsharp.politescraper;

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