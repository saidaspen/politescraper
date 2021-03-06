package se.redsharp.politescraper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.Random;

public final class PoliteScraper {

    /**
     * Time related constants.
     */
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int MS_PER_SECOND = 1000;
    private static final int MS_IN_MIN = MS_PER_SECOND * SECONDS_PER_MINUTE;

    @SuppressWarnings("WeakerAccess")
    public static final long DEFAULT_BACK_OFF_SECONDS = 2 * MS_IN_MIN;
    @SuppressWarnings("WeakerAccess")
    public static final long DEFAULT_MAX_BACK_OFF_SECONDS = 20 * MS_IN_MIN;
    @SuppressWarnings("WeakerAccess")
    public static final long DEFAULT_WAIT_LOAD = 5 * MS_PER_SECOND;
    @SuppressWarnings("WeakerAccess")
    public static final long DEFAULT_MAX_WAIT_LOAD = 10 * DEFAULT_WAIT_LOAD;
    @SuppressWarnings("WeakerAccess")
    public static final long DEFAULT_MIN_WAIT_BETWEEN = 10 * MS_PER_SECOND;
    @SuppressWarnings("WeakerAccess")
    public static final long DEFAULT_STD_DEV_WAIT_BETWEEN = 2 * DEFAULT_WAIT_LOAD;

    private static final String MSG_BACKING_OFF = "Backing off {} minutes.";
    private static final String MSG_INTERRUPTED_FROM_SLEEP = "Unexpectedly interrupted from sleep";
    private static final String MSG_PAGE_NOT_FINISHED = "Html had not been retrieved fully.";
    private static final String MSG_WAIT_BETWEEN_CALLS = "Wait between consecutive calls.\t {} seconds.";
    private static final String MSG_WAIT_PAGE_LOAD = "Waiting for page to load.";

    private final WebDriver driver;
    private final TimeProvider timeProvider;
    private final PageBrain brain;
    private final Random rand;

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

        private final WebDriver webDriver;
        private final PageBrain brain;
        private TimeProvider timeProvider;
        private long backOffMillis = DEFAULT_BACK_OFF_SECONDS;
        private long maxBackOff = DEFAULT_MAX_BACK_OFF_SECONDS;
        private long minWaitBetween = DEFAULT_MIN_WAIT_BETWEEN;
        private long waitLoad = DEFAULT_WAIT_LOAD;
        private long maxWaitLoad = DEFAULT_MAX_WAIT_LOAD;
        private long stdDevWaitBetween = DEFAULT_STD_DEV_WAIT_BETWEEN;
        private Random rand = new SecureRandom();

        public PoliteScraperBuilder(WebDriver webDriver, PageBrain brain) {
            this.webDriver = webDriver;
            this.brain = brain;
        }

        public PoliteScraperBuilder timeProvider(TimeProvider timeProvider) {
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
            maxBackOff = (long) seconds * MS_PER_SECOND;
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

        public PoliteScraperBuilder random(Random rand) {
            this.rand = rand;
            return this;
        }

        public PoliteScraper build() {
            if (timeProvider == null) {
                timeProvider = new DefaultTimeProvider();
            }
            return new PoliteScraper(this);
        }
    }

    private PoliteScraper(PoliteScraperBuilder builder) {
        driver = builder.webDriver;
        timeProvider = builder.timeProvider;
        brain = builder.brain;
        originalBackOfMillis = builder.backOffMillis;
        maxBackOffMillis = builder.maxBackOff;
        minWaitBetween = builder.minWaitBetween;
        waitLoad = builder.waitLoad;
        maxWaitLoad = builder.maxWaitLoad;
        stdDevWaitBetween = builder.stdDevWaitBetween;
        lastRequest = timeProvider.currentTimeMillis();
        rand = builder.rand;
        backOffMillis = originalBackOfMillis;
        log = LogManager.getLogger(getClass().getSimpleName());
    }

    public void run() {
        Optional<String> url = brain.nextUrl();
        while (url.isPresent()) {
            try {
                String html = pageOf(url.get());
                brain.notifyDone(url.get(), html);
            } catch (ScrapingException e) {
                brain.handleError(url.get(), e.getMessage());
            }
            url = brain.nextUrl();
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
        } catch (InterruptedException e) {
            throw new ScrapingException(MSG_INTERRUPTED_FROM_SLEEP, e);
        }
    }

    private int newBackOff() {
        return (int) (2 * backOffMillis + backOffMillis * rand.nextGaussian());
    }

    private String scrape(String url) throws ScrapingException {
        try {
            long waitBetweenCalls = (long) (Math.abs(rand.nextGaussian() * stdDevWaitBetween) + minWaitBetween);
            long waitFor = Math.max(minWaitBetween, lastRequest + waitBetweenCalls - timeProvider.currentTimeMillis());
            if (waitFor > 0) {
                log.info(MSG_WAIT_BETWEEN_CALLS, waitFor / 1000);
                timeProvider.sleep(waitFor);
            }
            log.info("Requesting URL: {}", url);
            lastRequest = timeProvider.currentTimeMillis();
            driver.get(url);
            return waitForPageLoad();
        } catch (InterruptedException e) {
            throw new ScrapingException(MSG_INTERRUPTED_FROM_SLEEP, e);
        }
    }

    private String waitForPageLoad() throws InterruptedException, ScrapingException {
        log.debug(MSG_WAIT_PAGE_LOAD);
        timeProvider.sleep(waitLoad);
        String html = driver.getPageSource();
        long currentTime = timeProvider.currentTimeMillis();
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

}
