package se.redsharp.politescraper;

public interface PageBrain {

    boolean shouldBackOffAndRetry(String scrapedHtml);

    void handleError(String url, String message);

    boolean isFinishedLoading(String html);

    void accept(String s);
}
