package se.redsharp.politescraper;

import java.util.*;

public interface PageBrain {

    void notifyDone(String url, String html);

    boolean shouldBackOffAndRetry(String scrapedHtml);

    boolean isFinishedLoading(String url, String html);

    void handleError(String url, String message);

    Optional<String> nextUrl();
}
