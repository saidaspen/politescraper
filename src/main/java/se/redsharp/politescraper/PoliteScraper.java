package se.redsharp.politescraper;

import java.security.*;
import java.util.*;
import org.apache.logging.log4j.*;
import org.openqa.selenium.*;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class PoliteScraper {

    /**
     * Time related constants.
     */
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int MS_PER_SECOND = 1000;
    private static final int MS_IN_MIN = MS_PER_SECOND * SECONDS_PER_MINUTE;

    public static final long DEFAULT_BACK_OFF_SECONDS = 2 * MS_IN_MIN;
    public static final long DEFAULT_MAX_BACK_OFF_SECONDS = 20 * MS_IN_MIN;
    public static final long DEFAULT_WAIT_LOAD = 5 * MS_PER_SECOND;
    public static final long DEFAULT_MAX_WAIT_LOAD = 10 * DEFAULT_WAIT_LOAD;
    public static final long DEFAULT_MIN_WAIT_BETWEEN = 10 * MS_PER_SECOND;
    public static final long DEFAULT_STD_DEV_WAIT_BETWEEN = 2 * DEFAULT_WAIT_LOAD;

    private static final String MSG_BACKING_OFF = "Backing off {} minutes.";
    private static final String MSG_INTERRUPTED_FROM_SLEEP = "Unexpectedly interrupted from sleep";
    private static final String MSG_PAGE_NOT_FINISHED = "Html had not been retrieved fully.";

    private final WebDriver driver;
    private final TimeProvider timeProvider;
    private final PageBrain brain;
    private final Random rand = new SecureRandom();
    private final WebCache cache;

    private final long minWaitBetween;
    private final long originalBackOfMillis;
    private final long maxBackOffMillis;
    private final long waitLoad;
    private final long maxWaitLoad;
    private final double stdDevWaitBetween;
    private final Logger log;

    private long lastRequest;
    private long backOffMillis;

    @SuppressWarnings({"unused", "WeakerAccess"})
    public static final class PoliteScraperBuilder {

        private final WebDriver driver;
        private final PageBrain brain;
        private final WebCache cache;
        private TimeProvider timeProvider;
        private long backOffMillis = DEFAULT_BACK_OFF_SECONDS;
        private long seed = System.nanoTime();
        private long maxBackoff = DEFAULT_MAX_BACK_OFF_SECONDS;
        private long minWaitBetween = DEFAULT_MIN_WAIT_BETWEEN;
        private long waitLoad = DEFAULT_WAIT_LOAD;
        private long maxWaitLoad = DEFAULT_MAX_WAIT_LOAD;
        private long stdDevWaitBetween = DEFAULT_STD_DEV_WAIT_BETWEEN;

        public PoliteScraperBuilder(final WebDriver driver, final PageBrain brain, WebCache cache) {
            this.driver = driver;
            this.brain = brain;
            this.cache = cache;
        }

        public PoliteScraperBuilder timeProvider(final TimeProvider timeProvider) {
            this.timeProvider = timeProvider;
            return this;
        }

        public PoliteScraperBuilder backOffSeconds(int seconds) {
            backOffMillis = (long) seconds * MS_PER_SECOND;
            return this;
        }

        public PoliteScraperBuilder minWaitBetween(int seconds) {
            minWaitBetween = (long) seconds * MS_PER_SECOND;
            return this;
        }

        public PoliteScraperBuilder stdDevWaitBetween(int seconds) {
            stdDevWaitBetween = (long) seconds * MS_PER_SECOND;
            return this;
        }

        public PoliteScraperBuilder maxBackOff(int seconds) {
            maxBackoff = (long) seconds * MS_PER_SECOND;
            return this;
        }

        public PoliteScraperBuilder waitLoad(int seconds) {
            waitLoad = (long) seconds * MS_PER_SECOND;
            return this;
        }

        public PoliteScraperBuilder maxWaitLoad(int seconds) {
            maxWaitLoad = (long) seconds * MS_PER_SECOND;
            return this;
        }

        public PoliteScraperBuilder seed(long seed) {
            this.seed = seed;
            return this;
        }

        public PoliteScraper build() {
            if (timeProvider == null) {
                timeProvider = new DefaultTimeProvider();
            }
            return new PoliteScraper(this);
        }
    }

    private static final class DefaultTimeProvider implements TimeProvider {

        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public void sleep(long millis) throws InterruptedException {
            Thread.sleep(millis);
        }
    }

    private PoliteScraper(final PoliteScraperBuilder builder) {
        driver = builder.driver;
        cache = builder.cache;
        timeProvider = builder.timeProvider;
        brain = builder.brain;
        originalBackOfMillis = builder.backOffMillis;
        maxBackOffMillis = builder.maxBackoff;
        minWaitBetween = builder.minWaitBetween;
        waitLoad = builder.waitLoad;
        maxWaitLoad = builder.maxWaitLoad;
        stdDevWaitBetween = builder.stdDevWaitBetween;
        lastRequest = timeProvider.currentTimeMillis();
        backOffMillis = originalBackOfMillis;
        log = LogManager.getLogger(getClass().getSimpleName());
        rand.setSeed(builder.seed);
    }

    public void run() {
        while (brain.hasNext()) {
            String url = brain.nextUrl().get();
            log.info("Next url {}", url);
            try {
                Optional<String> cachedPage = cache.get(url);
                if (cachedPage.isPresent()) {
                    log.info("Url {} already exist in cache.", url);
                    brain.notifyDone(url, cachedPage.get());
                } else {
                    String html = pageOf(url);
                    cache.insert(url, html);
                    brain.notifyDone(url, html);
                }
            } catch (ScrapingException e) {
                brain.handleError(url, e.getMessage());
            }
        }
    }

    private String pageOf(String url) throws ScrapingException {
        String html = scrape(url);
        if (brain.shouldBackOffAndRetry(html)) {
            backOff();
            return pageOf(url);
        }
        backOffMillis = originalBackOfMillis;
        return html;
    }

    private void backOff() throws ScrapingException {
        try {
            backOffMillis = backOffMillis < maxBackOffMillis ? newBackOff() : maxBackOffMillis;
            log.warn(MSG_BACKING_OFF, backOffMillis / MS_IN_MIN);
            timeProvider.sleep(backOffMillis);
        } catch (final InterruptedException e) {
            throw new ScrapingException(MSG_INTERRUPTED_FROM_SLEEP, e);
        }
    }

    private int newBackOff() {
        return (int) (2 * backOffMillis + backOffMillis * rand.nextGaussian());
    }

    private String scrape(final String url) throws ScrapingException {
        try {
            final long waitBetweenCalls = (long) (Math.abs(rand.nextGaussian() * stdDevWaitBetween) + minWaitBetween);
            long waitFor = lastRequest + waitBetweenCalls - timeProvider.currentTimeMillis();
            if (waitFor > 0) {
                timeProvider.sleep(waitFor);
            }
            log.info("Requesting URL: {}", url);
            lastRequest = timeProvider.currentTimeMillis();
            driver.get(url);
            return waitForPageLoad();
        } catch (final InterruptedException e) {
            throw new ScrapingException(MSG_INTERRUPTED_FROM_SLEEP, e);
        }
    }

    private String waitForPageLoad() throws InterruptedException, ScrapingException {
        timeProvider.sleep(waitLoad);
        final String html = driver.getPageSource();
        final long currentTime = timeProvider.currentTimeMillis();
        boolean timeOut = currentTime > lastRequest + maxWaitLoad;
        if (brain.isFinishedLoading(driver.getCurrentUrl(), html)) {
            return html;
        } else if (timeOut) {
            throw new ScrapingException("URL '" + driver.getCurrentUrl() + "' timed out after " + maxWaitLoad + " seconds.");
        } else {
            log.warn(MSG_PAGE_NOT_FINISHED);
            return waitForPageLoad();
        }
    }

    private boolean isEmpty(final String str) {
        return str == null || str.trim().isEmpty();
    }

}
