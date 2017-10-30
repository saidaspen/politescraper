package se.redsharp.politescraper;

import java.security.*;
import java.util.*;
import org.apache.logging.log4j.*;
import org.openqa.selenium.*;

@SuppressWarnings("unused")
public final class PoliteScraper {

    /**
     * Time related constants.
     */
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int MS_PER_SECOND = 1000;
    private static final int MS_IN_MIN = MS_PER_SECOND * SECONDS_PER_MINUTE;

    private static final int DEFAULT_BACK_OFF_SECONDS = 2 * MS_IN_MIN;
    private static final int DEFAULT_MAX_BACK_OFF_SECONDS = 20 * MS_IN_MIN;
    private static final int DEFAULT_WAIT_LOAD = 5 * MS_PER_SECOND;
    private static final int DEFAULT_MAX_WAIT_LOAD = 10 * DEFAULT_WAIT_LOAD;
    private static final int DEFAULT_MIN_WAIT_BETWEEN = 10 * MS_PER_SECOND;
    private static final int DEFAULT_STD_DEV_WAIT_BETWEEN = 2 * DEFAULT_WAIT_LOAD;

    private static final String MSG_BACKING_OFF = "Backing off {} minutes.";
    private static final String MSG_INTERRUPTED_FROM_SLEEP = "Unexpectedly interrupted from sleep";
    private static final String MSG_PAGE_NOT_FINISHED = "Html had not been retrieved fully.";

    private final WebCache webCache;
    private final WebDriver driver;
    private final TimeProvider timeProvider;
    private final Logger log;
    private final PageBrain brain;
    private final Random rand = new SecureRandom();
    private long lastRequest;

    private final int minWaitBetween;
    private final int originalBackOfMillis;
    private final int maxBackOffMillis;
    private final int waitLoad;
    private final long maxWaitLoad;
    private final double stdDevWaitBetween;

    private int backOffMillis;

    @SuppressWarnings("unused")
    public static final class PoliteScraperBuilder {

        private final WebDriver driver;
        private final WebCache cache;
        private final PageBrain brain;
        private TimeProvider timeProvider;
        private int backOffMillis = DEFAULT_BACK_OFF_SECONDS;
        private long seed = System.nanoTime();
        private int maxBackoff = DEFAULT_MAX_BACK_OFF_SECONDS;
        private int minWaitBetween = DEFAULT_MIN_WAIT_BETWEEN;
        private int waitLoad = DEFAULT_WAIT_LOAD;
        private int maxWaitLoad = DEFAULT_MAX_WAIT_LOAD;
        private int stdDevWaitBetween = DEFAULT_STD_DEV_WAIT_BETWEEN;

        public PoliteScraperBuilder(final WebDriver driver, final WebCache cache, final PageBrain brain) {
            this.driver = driver;
            this.cache = cache;
            this.brain = brain;
        }

        public PoliteScraperBuilder timeProvider(final TimeProvider timeProvider) {
            this.timeProvider = timeProvider;
            return this;
        }

        public PoliteScraperBuilder backOffMinutes(int seconds) {
            backOffMillis = seconds * MS_PER_SECOND;
            return this;
        }

        public PoliteScraperBuilder minWaitBetween(int seconds) {
            minWaitBetween = seconds * MS_PER_SECOND;
            return this;
        }

        public PoliteScraperBuilder stdDevWaitBetween(int seconds) {
            stdDevWaitBetween = seconds * MS_PER_SECOND;
            return this;
        }

        public PoliteScraperBuilder maxBackOff(int seconds) {
            maxBackoff = seconds * MS_PER_SECOND;
            return this;
        }

        public PoliteScraperBuilder waitLoad(int seconds) {
            waitLoad = seconds * MS_PER_SECOND;
            return this;
        }

        public PoliteScraperBuilder maxWaitLoad(int seconds) {
            maxWaitLoad = seconds * MS_PER_SECOND;
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
        webCache = builder.cache;
        driver = builder.driver;
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
        rand.setSeed(builder.seed);
        log = LogManager.getLogger(getClass().getSimpleName());
    }

    public void run() {
        while (webCache.hasNext()) {
            String url = webCache.getNextUrl();
            try {
                brain.accept(pageOf(url));
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
        if (brain.isFinishedLoading(html)) {
            return html;
        } else if (timeProvider.currentTimeMillis() > lastRequest + maxWaitLoad) {
            throw new ScrapingException("URL " + driver.getCurrentUrl() + "timed out after " + maxWaitLoad + " seconds.");
        } else {
            log.warn(MSG_PAGE_NOT_FINISHED);
            return waitForPageLoad();
        }
    }

    private boolean isEmpty(final String str) {
        return str == null || str.trim().isEmpty();
    }

}
