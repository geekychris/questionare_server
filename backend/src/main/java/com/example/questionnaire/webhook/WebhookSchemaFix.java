package com.example.questionnaire.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * The first webhook build left {@code webhook_deliveries.payload} as MySQL TINYTEXT (255 bytes), so
 * real responses could not be queued, and {@code ddl-auto: update} never changes a column's type.
 * At startup this widens it to MEDIUMTEXT when it finds a smaller text type. Other databases: nothing.
 */
@Component
public class WebhookSchemaFix {

    private static final Logger log = LoggerFactory.getLogger(WebhookSchemaFix.class);

    /** Payload size limit; above 65,535 MySQL uses MEDIUMTEXT. */
    public static final int PAYLOAD_LENGTH = 4_000_000;

    private final JdbcTemplate jdbc;

    public WebhookSchemaFix(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void widenPayload() {
        try {
            String product = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
            if (product == null || !product.toLowerCase(Locale.ROOT).contains("mysql")) {
                return;
            }
            List<String> types = jdbc.queryForList("select data_type from information_schema.columns where table_schema = database() "
                    + "and table_name = 'webhook_deliveries' and column_name = 'payload'", String.class);
            if (!types.isEmpty() && List.of("tinytext", "text").contains(types.get(0).toLowerCase(Locale.ROOT))) {
                jdbc.execute("alter table webhook_deliveries modify payload mediumtext not null");
                log.info("webhook_deliveries.payload widened from {} to mediumtext", types.get(0));
            }
        } catch (RuntimeException e) {
            log.warn("could not check webhook_deliveries.payload: {}", e.toString());
        }
    }
}
