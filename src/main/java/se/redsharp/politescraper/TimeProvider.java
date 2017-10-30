package se.redsharp.politescraper;

public interface TimeProvider {

    long currentTimeMillis();

    void sleep(long millis) throws InterruptedException;
}
