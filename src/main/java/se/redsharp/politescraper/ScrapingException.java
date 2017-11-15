package se.redsharp.politescraper;

/**
 *  Exception type to encapsulate any kind of error happening during scraping.
 */
public class ScrapingException extends Exception {

    private static final long serialVersionUID = 5843098092228540344L;

    public ScrapingException(String msg, Exception cause) {
        super(msg, cause);
    }

    public ScrapingException(String msg) {
        super(msg);
    }
}
