package se.redsharp.politescraper;

import java.util.*;

public interface WebCache {
    void insert(String url, String page);

    boolean contains(String url);

    Optional<String> get(String url);
}
