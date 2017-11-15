package se.redsharp.politescraper;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static se.redsharp.politescraper.PoliteScraper.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.openqa.selenium.*;
import se.redsharp.politescraper.PoliteScraper.*;

public final class PoliteScraperTest {

    private static final int SEED = 42;
    private static final String URL = "not really an url";
    private static final String SOURCE = "some page source";
    private final PageBrain brain = mock(PageBrain.class);
    private final WebDriver driver = mock(WebDriver.class);
    private final TimeProvider timeProvider = mock(TimeProvider.class);
    private final PoliteScraper defaultScraper = new PoliteScraperBuilder(driver, brain).seed(SEED).timeProvider(timeProvider).build();

    @BeforeEach
    public void setUp() throws Exception {
        when(driver.getCurrentUrl()).thenReturn(URL);
        when(driver.getPageSource()).thenReturn(SOURCE);
    }

    @Test
    public void returnsIfDoesNotHaveUrls() throws Exception {
        when(brain.nextUrl()).thenReturn(Optional.empty());
        defaultScraper.run();
        verify(brain, atMost(1)).nextUrl();
        verifyNoMoreInteractions(brain, driver);
    }

    @Test
    public void pageAlreadyInCache() throws Exception {
        when(brain.nextUrl()).thenReturn(Optional.of(URL)).thenReturn(Optional.empty());
        when(brain.isFinishedLoading(anyString(), anyString())).thenReturn(true);
        defaultScraper.run();
        verify(brain, times(1)).notifyDone(URL, SOURCE);
    }

    @Test
    public void defaultMinmumWaitBetweenCalls() throws Exception {
        when(timeProvider.currentTimeMillis())
                .thenReturn(0L)
                .thenReturn(1L);
        when(brain.nextUrl()).thenReturn(Optional.of(URL)).thenReturn(Optional.empty());
        when(brain.isFinishedLoading(anyString(), anyString())).thenReturn(true);
        defaultScraper.run();
        ArgumentCaptor<Long> longCaptor = ArgumentCaptor.forClass(Long.class);
        verify(timeProvider, atLeastOnce()).sleep(longCaptor.capture());
        assertThat(longCaptor.getAllValues().get(0), is(greaterThan(PoliteScraper.DEFAULT_MIN_WAIT_BETWEEN)));
    }

    @Test
    public void defaultWaitPageLoad() throws Exception {
        when(timeProvider.currentTimeMillis())
                .thenReturn(0L)
                .thenReturn(1L);
        when(brain.nextUrl()).thenReturn(Optional.of(URL)).thenReturn(Optional.empty());
        when(brain.isFinishedLoading(anyString(), anyString())).thenReturn(true);
        defaultScraper.run();
        ArgumentCaptor<Long> longCaptor = ArgumentCaptor.forClass(Long.class);
        verify(timeProvider, atLeastOnce()).sleep(longCaptor.capture());
        assertThat(longCaptor.getAllValues().get(1), is(equalTo(PoliteScraper.DEFAULT_WAIT_LOAD)));
    }

    @Test
    public void waitsUntilPageFullyLoaded() throws Exception {
        when(timeProvider.currentTimeMillis())
                .thenReturn(0L)
                .thenReturn(1L);
        when(brain.nextUrl()).thenReturn(Optional.of(URL)).thenReturn(Optional.empty());
        when(brain.isFinishedLoading(anyString(), anyString())).thenReturn(false).thenReturn(true);
        defaultScraper.run();
        ArgumentCaptor<Long> longCaptor = ArgumentCaptor.forClass(Long.class);
        verify(timeProvider, atLeastOnce()).sleep(longCaptor.capture());
        List<Long> sleepValues = longCaptor.getAllValues();
        assertThat(sleepValues.get(0), is(greaterThan(PoliteScraper.DEFAULT_MIN_WAIT_BETWEEN)));
        assertThat(sleepValues.get(1), is(equalTo(PoliteScraper.DEFAULT_WAIT_LOAD)));
    }

    @Test
    public void timesOutAfterMaxWait() throws Exception {
        when(timeProvider.currentTimeMillis())
                .thenReturn(0L) // Getting time between calls
                .thenReturn(1L) // Last request
                .thenReturn(DEFAULT_MAX_WAIT_LOAD + 10);
        when(brain.nextUrl()).thenReturn(Optional.of(URL)).thenReturn(Optional.empty());
        when(brain.isFinishedLoading(anyString(), anyString())).thenReturn(false);
        defaultScraper.run();
        ArgumentCaptor<Long> longCaptor = ArgumentCaptor.forClass(Long.class);
        verify(timeProvider, atLeastOnce()).sleep(longCaptor.capture());
        List<Long> sleepValues = longCaptor.getAllValues();
        assertThat(sleepValues.get(0), is(greaterThan(PoliteScraper.DEFAULT_MIN_WAIT_BETWEEN)));
        assertThat(sleepValues.get(1), is(equalTo(PoliteScraper.DEFAULT_WAIT_LOAD)));
        verify(brain).handleError(URL, "URL '" + URL + "' timed out after 50000 seconds.");
    }
}