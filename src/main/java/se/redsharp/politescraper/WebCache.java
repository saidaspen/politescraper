package se.redsharp.politescraper;

public interface WebCache {

    String getNextUrl();

    String get(String url);

    void insert(String url, String scrapedHtml);

    boolean hasNext();
}
