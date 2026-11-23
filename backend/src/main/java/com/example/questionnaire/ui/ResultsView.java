package com.example.questionnaire.ui;

import com.example.questionnaire.dto.CampaignResultsDTO;
import com.example.questionnaire.security.CurrentUser;
import com.example.questionnaire.service.CampaignService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.NotFoundException;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.web.server.ResponseStatusException;

/** Answers per question: counts for choice questions, the texts for free-text ones. */
@Route(value = "campaigns/results", layout = MainLayout.class)
@PageTitle("Results | Questionnaire")
@RolesAllowed({"ADMIN", "CREATOR"})
public class ResultsView extends VerticalLayout implements HasUrlParameter<Long> {

    private final CampaignService service;

    public ResultsView(CampaignService service) {
        this.service = service;
        setMaxWidth("60rem");
    }

    @Override
    public void setParameter(BeforeEvent event, Long id) {
        removeAll();
        CampaignResultsDTO r;
        try {
            r = service.getResults(id, CurrentUser.current());
        } catch (ResponseStatusException e) {
            throw new NotFoundException("Campaign not found");
        }
        add(new Button("← Campaigns", e -> UI.getCurrent().navigate(CampaignsView.class)));
        add(new H2(r.getTitle()), new Paragraph(r.getSubmissions() + " submission(s)"));
        for (CampaignResultsDTO.QuestionResult q : r.getQuestions()) {
            Div box = new Div(new H4(q.getText()));
            if (!q.getOptions().isEmpty()) {
                long total = q.getOptions().stream().mapToLong(CampaignResultsDTO.OptionCount::getCount).sum();
                UnorderedList ul = new UnorderedList();
                for (CampaignResultsDTO.OptionCount o : q.getOptions()) {
                    long pct = total == 0 ? 0 : Math.round(100.0 * o.getCount() / total);
                    ul.add(new ListItem(o.getText() + ": " + o.getCount() + " (" + pct + "%)"));
                }
                box.add(ul);
            } else if (q.getTextAnswers().isEmpty()) {
                box.add(new Paragraph("No answers yet."));
            } else {
                UnorderedList ul = new UnorderedList();
                q.getTextAnswers().forEach(t -> ul.add(new ListItem(t)));
                box.add(ul);
            }
            add(box);
        }
    }
}
