package com.example.questionnaire.config;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

@Push
@Theme(value = "lumo", variant = Lumo.LIGHT)
public class ThemeConfig implements AppShellConfigurator {
    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addLink("stylesheet", "frontend/styles/shared-styles.css");
        settings.addMetaTag("viewport", "width=device-width, initial-scale=1.0");
        settings.addFavIcon("icon", "icons/icon.png", "192x192");
    }
}

