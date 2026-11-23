package com.example.questionnaire.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * "Follow the request's port": when the browser reached us on https with an explicit non-443
 * port (Belmont's LAN path, e.g. {@code survey.hitorro.com:8443}), the provider is reachable on
 * that same port, so browser-facing provider URLs (authorize, end session) get that port too.
 * Relies on {@code server.forward-headers-strategy=framework} so the request reflects
 * X-Forwarded-Host/Proto.
 */
public final class RequestPortFollower {

    private RequestPortFollower() {
    }

    /** The explicit public https port of the request, or -1 when it is the default (443) or not https. */
    public static int publicPort(HttpServletRequest request) {
        if (request == null || !"https".equalsIgnoreCase(request.getScheme())) {
            return -1;
        }
        int port = request.getServerPort();
        return port > 0 && port != 443 ? port : -1;
    }

    /**
     * Returns {@code uri} with the request's explicit port applied, but only when {@code uri}
     * points at the provider ({@code issuerBase}, compared on scheme and host) and has no port of its own.
     */
    public static String apply(String uri, String issuerBase, HttpServletRequest request) {
        int port = publicPort(request);
        if (port < 0 || uri == null || issuerBase == null) {
            return uri;
        }
        UriComponents target = UriComponentsBuilder.fromUriString(uri).build(true);
        UriComponents issuer = UriComponentsBuilder.fromUriString(issuerBase).build();
        if (target.getPort() != -1
                || !"https".equalsIgnoreCase(target.getScheme())
                || target.getHost() == null
                || !target.getHost().equalsIgnoreCase(issuer.getHost())) {
            return uri;
        }
        return UriComponentsBuilder.fromUriString(uri).port(port).build(true).toUriString();
    }
}
