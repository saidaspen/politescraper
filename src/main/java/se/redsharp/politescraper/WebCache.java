package se.redsharp.politescraper;

public interface WebCache {
    void insert(String url, String page);

    boolean contains(String url);
}
