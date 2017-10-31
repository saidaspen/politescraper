package se.redsharp.politescraper;

class ScrapingException extends Throwable {

    private static final long serialVersionUID = 5843098092228540344L;

    ScrapingException(String msg, Exception cause) {
        super(msg, cause);
    }

    ScrapingException(String msg) {
        super(msg);
    }
}
