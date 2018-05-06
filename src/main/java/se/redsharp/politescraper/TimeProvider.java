package se.redsharp.politescraper;

/**
 * Interface for any mechanism for providing the current time in milliseconds.
 * This interface is mainly to making users of it testable.
 */
interface TimeProvider {

    long currentTimeMillis();

    void sleep(long millis) throws InterruptedException;
}
