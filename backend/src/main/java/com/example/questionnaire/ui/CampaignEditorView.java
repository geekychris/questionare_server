package com.example.questionnaire.ui;

import com.example.questionnaire.dto.AnswerOptionDTO;
import com.example.questionnaire.dto.CampaignDTO;
import com.example.questionnaire.dto.QuestionDTO;
import com.example.questionnaire.security.CurrentUser;
import com.example.questionnaire.service.CampaignService;
import com.example.questionnaire.webhook.WebhookService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.NotFoundException;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Create or edit a campaign. Questions can be edited until the first response arrives
 * (responses reference them); after that only the campaign's own fields change.
 */
@Route(value = "campaigns/edit", layout = MainLayout.class)
@PageTitle("Edit campaign | Questionnaire")
@RolesAllowed({"ADMIN", "CREATOR"})
public class CampaignEditorView extends VerticalLayout implements HasUrlParameter<Long> {

    private final CampaignService service;
    private final WebhookService webhooks;
    private final CurrentUser user;

    private final TextField title = new TextField("Title");
    private final TextArea description = new TextArea("Description");
    private final Checkbox active = new Checkbox("Open for responses");
    private final Checkbox requireLogin = new Checkbox("Require sign-in to answer (any questionnaire role)");
    private final DateTimePicker endDate = new DateTimePicker("Closes at (optional)");
    private final VerticalLayout questionList = new VerticalLayout();
    private final List<QuestionEditor> editors = new ArrayList<>();
    private Long id;
    private boolean questionsLocked;

    public CampaignEditorView(CampaignService service, WebhookService webhooks) {
        this.service = service;
        this.webhooks = webhooks;
        this.user = CurrentUser.current();
        setMaxWidth("60rem");
        title.setWidthFull();
        title.setMaxLength(255);
        description.setWidthFull();
        description.setMaxLength(255);
        questionList.setPadding(false);
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter Long parameter) {
        removeAll();
        editors.clear();
        questionList.removeAll();
        this.id = parameter;
        CampaignDTO c;
        if (parameter == null) {
            c = new CampaignDTO();
            c.setActive(true);
        } else {
            try {
                c = service.getCampaign(parameter, user);
            } catch (ResponseStatusException e) {
                throw new NotFoundException("Campaign not found");
            }
        }
        questionsLocked = c.getResponseCount() > 0;
        title.setValue(c.getTitle() == null ? "" : c.getTitle());
        description.setValue(c.getDescription() == null ? "" : c.getDescription());
        active.setValue(c.isActive());
        requireLogin.setValue(c.isRequireLogin());
        endDate.setValue(c.getEndDate());

        add(new H2(parameter == null ? "New campaign" : "Edit campaign"), title, description, active, requireLogin, endDate);
        add(new H2("Questions"));
        if (questionsLocked) {
            add(new Paragraph("This campaign has responses, so its questions can no longer be changed."));
        }
        c.getQuestions().forEach(this::addQuestion);
        add(questionList);
        if (!questionsLocked) {
            add(new Button("Add question", VaadinIcon.PLUS.create(), e -> addQuestion(null)));
        }
        Button save = new Button("Save", e -> save());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", e -> UI.getCurrent().navigate(CampaignsView.class));
        add(new HorizontalLayout(save, cancel));
        if (parameter != null) {
            add(new WebhookPanel(webhooks, user, parameter));
        }
    }

    private void addQuestion(QuestionDTO q) {
        QuestionEditor ed = new QuestionEditor(q, questionsLocked);
        ed.remove.addClickListener(e -> {
            editors.remove(ed);
            questionList.remove(ed);
        });
        editors.add(ed);
        questionList.add(ed);
    }

    private void save() {
        CampaignDTO dto = new CampaignDTO();
        dto.setTitle(title.getValue().trim());
        dto.setDescription(description.getValue().trim());
        dto.setActive(active.getValue());
        dto.setRequireLogin(requireLogin.getValue());
        dto.setEndDate(endDate.getValue());
        List<QuestionDTO> questions = new ArrayList<>();
        for (int i = 0; i < editors.size(); i++) {
            questions.add(editors.get(i).toDto(i + 1));
        }
        try {
            if (id == null) {
                dto.setQuestions(questions);
                service.createCampaign(dto, user);
            } else {
                service.updateCampaign(id, dto, user);
                if (!questionsLocked) {
                    service.replaceQuestions(id, questions, user);
                }
            }
            Notification.show("Saved");
            UI.getCurrent().navigate(CampaignsView.class);
        } catch (ResponseStatusException e) {
            Notification n = Notification.show(e.getReason() == null ? "Could not save" : e.getReason());
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /** One question: text, type, required, and (for choice questions) one option per line. */
    private static final class QuestionEditor extends VerticalLayout {
        private final TextField text = new TextField("Question");
        private final Select<String> type = new Select<>();
        private final Checkbox required = new Checkbox("Required");
        private final TextArea options = new TextArea("Options (one per line)");
        private final Button remove = new Button(VaadinIcon.TRASH.create());

        QuestionEditor(QuestionDTO q, boolean locked) {
            getStyle().set("border", "1px solid var(--lumo-contrast-20pct)").set("border-radius", "var(--lumo-border-radius-m)");
            text.setWidthFull();
            text.setMaxLength(255);
            type.setLabel("Type");
            type.setItems("MULTIPLE_CHOICE", "TEXT");
            type.setItemLabelGenerator(t -> "TEXT".equals(t) ? "Free text" : "Multiple choice");
            options.setWidthFull();
            remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            remove.setTooltipText("Remove question");
            if (q != null) {
                text.setValue(q.getText() == null ? "" : q.getText());
                type.setValue("TEXT".equalsIgnoreCase(q.getType()) ? "TEXT" : "MULTIPLE_CHOICE");
                required.setValue(q.isRequired());
                options.setValue(String.join("\n", q.getOptions().stream().map(AnswerOptionDTO::getText).toList()));
            } else {
                type.setValue("MULTIPLE_CHOICE");
            }
            options.setVisible(!"TEXT".equals(type.getValue()));
            type.addValueChangeListener(e -> options.setVisible(!"TEXT".equals(e.getValue())));
            HorizontalLayout row = new HorizontalLayout(type, required, remove);
            row.setAlignItems(Alignment.BASELINE);
            add(text, row, options);
            if (locked) {
                text.setReadOnly(true);
                type.setReadOnly(true);
                required.setReadOnly(true);
                options.setReadOnly(true);
                remove.setVisible(false);
            }
        }

        QuestionDTO toDto(int order) {
            QuestionDTO q = new QuestionDTO();
            q.setText(text.getValue().trim());
            q.setType(type.getValue());
            q.setRequired(required.getValue());
            q.setOrderIndex(order);
            List<AnswerOptionDTO> opts = new ArrayList<>();
            if (!"TEXT".equals(type.getValue())) {
                List<String> lines = Arrays.stream(options.getValue().split("\\R"))
                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
                for (int i = 0; i < lines.size(); i++) {
                    AnswerOptionDTO o = new AnswerOptionDTO();
                    o.setText(lines.get(i));
                    o.setOrderIndex(i + 1);
                    opts.add(o);
                }
            }
            q.setOptions(opts);
            return q;
        }
    }
}
