package se.redsharp.politescraper;

import java.util.*;

/**
 * Implementations of PageBrain knows the specifics of the site being scraped.
 * <p>
 * The PageBrain implementation keep track of what URLs to scrape, how that queue of URLs changes over time as
 * pages are scraped (simulating clicking on links on the actual pages), is able to determine if a page is fully loaded
 * or not and keeps track and decides what to do if something goes wrong.
 * <p>
 * Normally PageBrain implementation needs to have two parts. One part is the logic related to the specific site in
 * question and the other part is some means of storing and handling the queue of URLs to be scraped.
 */
public interface PageBrain {

    /**
     * This method will return the next link to be scraped.
     * <p>
     * If there are no more links to be parsed, then it will return an empty {@link Optional}.
     * <p>
     * Normally the PageBrain needs to keep a list of links to be parsed. This list can be stored in database, in a
     * flat-file on disk or any other way. The list is normally not static, it might change when a page is scraped
     * such that it is possible to "follow" links from one page to another.
     *
     * @return empty {@link Optional} if there are no more URLs to be scraped, otherwise returns the string
     * representation of the URL to be parsed.
     */
    Optional<String> nextUrl();

    /**
     * Method which the {@link PageBrain} uses to inform the scraper if the html-page in question is fully loaded.
     * <p>
     * Many times the data received for web-pages is sent asynchronously. This is often controlled by Java-scripts which
     * requests data in several batches from the server. Therefore at any given point the page might or might not be
     * fully loaded.
     * The engine for scraping the web-pages, does not know any specifics of the pages being scraped so it is up to the
     * {@link PageBrain} to be able to tell the scraper if the page is fully loaded or not.
     *
     * @param url  the URL which has been scraped in order to produce the html
     * @param html the full string representation of the web-page. Might or might not be fully loaded.
     * @return true if the page is considered to be fully loaded. Else returns false.
     */
    boolean isFinishedLoading(String url, String html);

    /**
     * Method which the {@link PageBrain} uses to inform the scraper if the html-page returned is wrong.
     * This can happen if the site which is being scraped returns some other page, a 404 or a Request Rejected page
     * because it has been hit too hard with scraping or for some other reason.
     * <p>
     * When this happens the scraper will try to back-off. To wait a longer amount of time before returning to scraping.
     *
     * @param scrapedHtml
     * @return
     */
    boolean shouldBackOffAndRetry(String scrapedHtml);

    /**
     * Call back done by scraper to inform the {@link PageBrain} that a certain page is now handled.
     * <p>
     * Normally what happens in the {@link PageBrain} at this point is to remove the url as handled in its internal
     * queue (or mark it as handled in some way). Parse the actual web-page being returned and update the queue of urls
     * to be parsed with any links found that it should follow up on.
     *
     * @param url  the URL which has been scraped in order to produce the html
     * @param html the full html of the page in string format. At this point the page should be fully loaded.
     */
    void notifyDone(String url, String html);

    /**
     * Method used by the scraper to inform the {@link PageBrain} that something has gone wrong.
     * The {@link PageBrain} can then decide what to do. Log it and keep going or stop processing altogether.
     *
     * @param url     the url being scraped at the time of the error.
     * @param message string representation of the underlying exception.
     */
    void handleError(String url, String message);
}
