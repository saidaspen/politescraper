package se.redsharp.politescraper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openqa.selenium.WebDriver;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static se.redsharp.politescraper.PoliteScraper.*;

final class PoliteScraperTest {

    private static final String URL = "not really an url";
    private static final String SOURCE = "some page source";
    private final PageBrain brain = mock(PageBrain.class);
    private final WebDriver driver = mock(WebDriver.class);
    private final TimeProvider timeProvider = mock(TimeProvider.class);
    private final Random rand = mock(Random.class);
    private final PoliteScraper defaultScraper = new PoliteScraperBuilder(driver, brain)
            .random(rand)
            .timeProvider(timeProvider)
            .build();

    @BeforeEach
    void setUp() {
        when(rand.nextGaussian()).thenReturn(1.0).thenReturn(1.0);
        when(driver.getCurrentUrl()).thenReturn(URL);
        when(driver.getPageSource()).thenReturn(SOURCE);
    }

    @Test
    void returnsIfDoesNotHaveUrls() {
        when(brain.nextUrl()).thenReturn(Optional.empty());
        defaultScraper.run();
        verify(brain, atMost(1)).nextUrl();
        verifyNoMoreInteractions(brain, driver);
    }

    @Test
    void pageAlreadyInCache() {
        when(brain.nextUrl()).thenReturn(Optional.of(URL)).thenReturn(Optional.empty());
        when(brain.isFinishedLoading(anyString(), anyString())).thenReturn(true);
        defaultScraper.run();
        verify(brain, times(1)).notifyDone(URL, SOURCE);
    }

    @Test
    void defaultMinimumWaitBetweenCalls() throws InterruptedException {
        testMinWaitBetween(defaultScraper, DEFAULT_MIN_WAIT_BETWEEN);
    }

    @Test
    void configurableMinWaitBetween() throws InterruptedException {
        PoliteScraper scraper = new PoliteScraperBuilder(driver, brain)
                .random(rand)
                .timeProvider(timeProvider)
                .minWaitBetween(9)
                .build();
        testMinWaitBetween(scraper, 9 * 1000L);
    }

    @Test
    void configurableStdWaitBetween() throws InterruptedException {
        PoliteScraper scraper = new PoliteScraperBuilder(driver, brain)
                .random(rand)
                .timeProvider(timeProvider)
                .stdDevWaitBetween(100)
                .minWaitBetween(1).build();
        testMinWaitBetween(scraper, 34676L - 1);
    }

    private void testMinWaitBetween(PoliteScraper scraper, long minWait) throws InterruptedException {
        when(timeProvider.currentTimeMillis())
                .thenReturn(0L)
                .thenReturn(1L);
        when(brain.nextUrl()).thenReturn(Optional.of(URL)).thenReturn(Optional.empty());
        when(brain.isFinishedLoading(anyString(), anyString())).thenReturn(true);
        scraper.run();
        ArgumentCaptor<Long> longCaptor = ArgumentCaptor.forClass(Long.class);
        verify(timeProvider, atLeastOnce()).sleep(longCaptor.capture());
        assertThat(longCaptor.getAllValues().get(0), is(greaterThan(minWait)));
    }

    @Test
    void defaultWaitPageLoad() throws InterruptedException {
        testWaitLoad(defaultScraper, DEFAULT_WAIT_LOAD);
    }

    @Test
    void configurableWaitLoad() throws InterruptedException {
        PoliteScraper scraper = new PoliteScraperBuilder(driver, brain)
                .random(rand)
                .timeProvider(timeProvider)
                .waitLoad(5)
                .build();
        testWaitLoad(scraper, 5 * 1000L);
    }

    private void testWaitLoad(PoliteScraper scraper, long expectedWaitLoad) throws InterruptedException {
        when(timeProvider.currentTimeMillis())
                .thenReturn(0L)
                .thenReturn(1L);
        when(brain.nextUrl()).thenReturn(Optional.of(URL)).thenReturn(Optional.empty());
        when(brain.isFinishedLoading(anyString(), anyString())).thenReturn(true);
        scraper.run();
        ArgumentCaptor<Long> longCaptor = ArgumentCaptor.forClass(Long.class);
        verify(timeProvider, atLeastOnce()).sleep(longCaptor.capture());
        assertThat(longCaptor.getAllValues().get(1), is(equalTo(expectedWaitLoad)));
    }

    @Test
    void waitsUntilPageFullyLoaded() throws InterruptedException {
        when(timeProvider.currentTimeMillis())
                .thenReturn(0L)
                .thenReturn(1L);
        when(brain.nextUrl()).thenReturn(Optional.of(URL)).thenReturn(Optional.empty());
        when(brain.isFinishedLoading(anyString(), anyString())).thenReturn(false).thenReturn(true);
        defaultScraper.run();
        ArgumentCaptor<Long> longCaptor = ArgumentCaptor.forClass(Long.class);
        verify(timeProvider, atLeastOnce()).sleep(longCaptor.capture());
        List<Long> sleepValues = longCaptor.getAllValues();
        assertThat(sleepValues.get(0), is(greaterThan(DEFAULT_MIN_WAIT_BETWEEN)));
        assertThat(sleepValues.get(1), is(equalTo(DEFAULT_WAIT_LOAD)));
    }

    @Test
    void timesOutAfterMaxWait() throws InterruptedException {
        testTimeout(defaultScraper, DEFAULT_MAX_WAIT_LOAD);
    }

    @Test
    void configurableTimeout() throws InterruptedException {
        PoliteScraper scraper = new PoliteScraperBuilder(driver, brain)
                .random(rand)
                .timeProvider(timeProvider)
                .maxWaitLoad(9)
                .build();
        testTimeout(scraper, 9 * 1000);
    }

    private void testTimeout(PoliteScraper scraper, long timeout) throws InterruptedException {
        when(timeProvider.currentTimeMillis())
                .thenReturn(0L) // Getting time between calls
                .thenReturn(1L) // Last request
                .thenReturn(timeout + 10);
        when(brain.nextUrl()).thenReturn(Optional.of(URL)).thenReturn(Optional.empty());
        when(brain.isFinishedLoading(anyString(), anyString())).thenReturn(false);
        scraper.run();
        ArgumentCaptor<Long> longCaptor = ArgumentCaptor.forClass(Long.class);
        verify(timeProvider, atLeastOnce()).sleep(longCaptor.capture());
        List<Long> sleepValues = longCaptor.getAllValues();
        assertThat(sleepValues.get(0), is(greaterThan(DEFAULT_MIN_WAIT_BETWEEN)));
        assertThat(sleepValues.get(1), is(equalTo(DEFAULT_WAIT_LOAD)));
        verify(brain).handleError(URL, "URL '" + URL + "' timed out after " + timeout + " seconds.");
    }

    @Test
    void defaultBackOff() throws Exception {
        testBackOff(defaultScraper, DEFAULT_BACK_OFF_SECONDS);
    }

    @Test
    void configurableBackOff() throws Exception {
        PoliteScraper scraper = new PoliteScraperBuilder(driver, brain)
                .random(rand)
                .timeProvider(timeProvider)
                .backOffSeconds(9).build();
        testBackOff(scraper, 9 * 1000);
    }

    @Test
    void configurableMaxBackOff() throws Exception {
        PoliteScraper scraper = new PoliteScraperBuilder(driver, brain)
                .random(rand)
                .timeProvider(timeProvider)
                .backOffSeconds(10)
                .maxBackOff(6).build();
        testBackOff(scraper, 5 * 1000);
    }

    @Test
    void handlesErrorOnUnderlyingInterruption() throws InterruptedException {
        when(timeProvider.currentTimeMillis())
                .thenReturn(0L) // Getting time between calls
                .thenReturn(1L); // Last request
        doThrow(new InterruptedException("some exception msg")).when(timeProvider).sleep(anyLong());
        when(brain.nextUrl()).thenReturn(Optional.of(URL)).thenReturn(Optional.of(URL)).thenReturn(Optional.empty());
        when(brain.isFinishedLoading(anyString(), anyString())).thenReturn(true);
        when(brain.shouldBackOffAndRetry(anyString())).thenReturn(true).thenReturn(false);
        defaultScraper.run();
        verify(brain, atLeastOnce()).handleError(URL, "Unexpectedly interrupted from sleep");
    }

    @Test
    void handlesErrorOnUnderlyingBackOffInterruption() throws InterruptedException {
        when(timeProvider.currentTimeMillis()).thenReturn(0L).thenReturn(1L);
        doNothing().doNothing().doThrow(new InterruptedException("some exception msg")).when(timeProvider).sleep(anyLong());
        when(brain.nextUrl()).thenReturn(Optional.of(URL)).thenReturn(Optional.empty());
        when(brain.isFinishedLoading(anyString(), anyString())).thenReturn(true);
        when(brain.shouldBackOffAndRetry(anyString())).thenReturn(true).thenReturn(false);
        defaultScraper.run();
        ArgumentCaptor<Long> longCaptor = ArgumentCaptor.forClass(Long.class);
        verify(timeProvider, atLeastOnce()).sleep(longCaptor.capture());
        verify(brain, atLeastOnce()).handleError(URL, "Unexpectedly interrupted from sleep");
    }

    @Test
    void buildDefaultTimeProvider() {
        Integer minWait = 1;
        PoliteScraper scraper = new PoliteScraperBuilder(driver, brain)
                .random(rand)
                .minWaitBetween(minWait)
                .stdDevWaitBetween(0)
                .waitLoad(0)
                .maxWaitLoad(1)
                .build();
        when(brain.nextUrl()).thenReturn(Optional.of(URL)).thenReturn(Optional.empty());
        when(brain.isFinishedLoading(anyString(), anyString())).thenReturn(true);
        long timeBefore = System.currentTimeMillis();
        scraper.run();
        long timeAfter = System.currentTimeMillis();
        assertThat(timeAfter - timeBefore, is(greaterThanOrEqualTo(minWait * 1000L)));
        assertThat(timeAfter - timeBefore, is(lessThanOrEqualTo(2 * minWait * 1000L)));
        verify(brain, times(1)).notifyDone(URL, SOURCE);
    }

    private void testBackOff(PoliteScraper scraper, long backOffSeconds) throws InterruptedException {
        when(timeProvider.currentTimeMillis())
                .thenReturn(0L) // Getting time between calls
                .thenReturn(1L); // Last request
        when(brain.nextUrl()).thenReturn(Optional.of(URL)).thenReturn(Optional.empty());
        when(brain.isFinishedLoading(anyString(), anyString())).thenReturn(true);
        when(brain.shouldBackOffAndRetry(anyString())).thenReturn(true).thenReturn(false);
        scraper.run();
        ArgumentCaptor<Long> longCaptor = ArgumentCaptor.forClass(Long.class);
        verify(timeProvider, atLeastOnce()).sleep(longCaptor.capture());
        List<Long> sleepValues = longCaptor.getAllValues();
        assertThat(sleepValues.get(0), is(greaterThan(DEFAULT_MIN_WAIT_BETWEEN)));
        assertThat(sleepValues.get(1), is(equalTo(DEFAULT_WAIT_LOAD)));
        assertThat(sleepValues.get(2), is(greaterThan(backOffSeconds - DEFAULT_WAIT_LOAD)));
        assertThat(sleepValues.get(3), is(greaterThan(DEFAULT_MIN_WAIT_BETWEEN)));
        assertThat(sleepValues.get(4), is(equalTo(DEFAULT_WAIT_LOAD)));
    }
}