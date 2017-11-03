package se.redsharp.politescraper;

interface TimeProvider {

    long currentTimeMillis();

    void sleep(long millis) throws InterruptedException;
}
