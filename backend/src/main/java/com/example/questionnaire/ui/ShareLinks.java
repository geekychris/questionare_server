package com.example.questionnaire.ui;

import com.vaadin.flow.server.VaadinServletRequest;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Absolute share link: APP_PUBLIC_URL when configured (already in the DTO), else this request's origin. */
final class ShareLinks {

    private ShareLinks() {
    }

    static String absolute(String shareUrl) {
        if (shareUrl == null || !shareUrl.startsWith("/")) {
            return shareUrl;
        }
        VaadinServletRequest req = VaadinServletRequest.getCurrent();
        if (req == null) {
            return shareUrl;
        }
        return ServletUriComponentsBuilder.fromContextPath(req.getHttpServletRequest()).build().toUriString() + shareUrl;
    }
}
