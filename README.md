## Polite Scraper

Polite Scraper is a very small library meant to be used as a polite web-scraper. 

The web is full of wonderful data. But in order to get our hands on that data we need to write scrapers and webcrawlers to download the pages for us.
If we do this naivly we might build scrapers that disturb the actual services and sites that we want to scrape. It can be a hassel for the system administrators.
We don't want to do that. We want to be polite.

Don't scale this up into a multi-threaded DDOS monstrousity. 

Don't be in a hurry. Be polite. Be responsible.

## Key features
* Uses Selenium headless browser as driver.
* Back-off when requests are rejected.
* Randomizes time between sequential requests according to guassian distribution.
* Add one class (implementing interface PageBrain) for each type of web-site you want to scrape.
* It does not do concurrent requests (by design)
* It defaults to having quite long waiting in between requests.

## Getting started

## Webscraping - Be polite
When scraping web-pages you should be polite. Make sure that you do not scale up your scraping to a level which affects or degrades the service that the site is providing.
Don't be a pain for the sys-admins of the web-page.

Before you start scraping a web-page, best practice is to check for a robot.txt file in the root of the domain.
Sysadmins put a robot.txt file there with instructions and policy-rules for webcrawlers and scrapers. To be polite, follow the instructions and rules specified in it.

## How To Use

## Dependencies and credits
Polite Scraper uses several other open source projects.

* [Selenium](http://www.seleniumhq.org/)
* [SLF4J](https://www.slf4j.org/)
* [Phtanom JS](http://phantomjs.org/)
* [JUnit](http://junit.org/junit5/)
* [Mockito](http://site.mockito.org/)
* [Hamcrest](http://hamcrest.org/)

#### License

MIT

---
[saidaspen.com](http://www.saidaspen.com) &nbsp;&middot;&nbsp;
GitHub [@saidaspen](https://github.com/saidaspen)

