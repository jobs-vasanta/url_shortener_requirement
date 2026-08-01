package com.urlshortener.service;

/**
 * Request-context fields captured at redirect time and forwarded into the click
 * analytics pipeline. A parameter object instead of three loose strings, since
 * these three always travel together (see UrlServiceImpl.resolveForRedirect).
 */
public record ClickContext(String referrer, String userAgent, String remoteIp) {
}
