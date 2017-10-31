package se.redsharp.politescraper;

public interface PageBrain {

    boolean hasNext();

    String nextUrl();

    void notifyDone(String s);

    boolean shouldBackOffAndRetry(String scrapedHtml);

    boolean isFinishedLoading(String html);

    void handleError(String url, String message);
}
