package com.example.questionnaire.service;

import com.example.questionnaire.dto.AnswerOptionDTO;
import com.example.questionnaire.dto.CampaignDTO;
import com.example.questionnaire.dto.CampaignResultsDTO;
import com.example.questionnaire.dto.QuestionDTO;
import com.example.questionnaire.model.AnswerOption;
import com.example.questionnaire.model.Campaign;
import com.example.questionnaire.model.Question;
import com.example.questionnaire.model.UserResponse;
import com.example.questionnaire.repository.CampaignRepository;
import com.example.questionnaire.repository.UserResponseRepository;
import com.example.questionnaire.security.CurrentUser;
import com.example.questionnaire.security.ShareTokens;
import com.example.questionnaire.webhook.WebhookDeliveryRepository;
import com.example.questionnaire.webhook.WebhookProperties;
import com.example.questionnaire.webhook.WebhookService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Campaign management for admins and creators. Access rules:
 * admin sees and manages every campaign and may delete; a creator sees and manages only the
 * campaigns they created (no delete). A campaign the caller may not see is reported as 404.
 */
@Service
public class CampaignService {

    public static final int MAX_QUESTIONS = 200;
    public static final int MAX_OPTIONS = 50;
    public static final int MAX_TEXT = 1000;

    private final CampaignRepository campaignRepository;
    private final UserResponseRepository responseRepository;
    private final String publicUrl;
    private final WebhookProperties webhookProps;
    private final WebhookDeliveryRepository webhookDeliveries;
    private final ApplicationEventPublisher events;

    public CampaignService(CampaignRepository campaignRepository,
                           UserResponseRepository responseRepository,
                           @Value("${app.public-url:}") String publicUrl,
                           WebhookProperties webhookProps,
                           WebhookDeliveryRepository webhookDeliveries,
                           ApplicationEventPublisher events) {
        this.events = events;
        this.campaignRepository = campaignRepository;
        this.responseRepository = responseRepository;
        this.webhookProps = webhookProps;
        this.webhookDeliveries = webhookDeliveries;
        String p = publicUrl == null ? "" : publicUrl.trim();
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        this.publicUrl = p;
    }

    /** Published when a campaign stops accepting responses; webhooks act on it after commit. */
    public record CampaignClosed(Long campaignId) {}

    @Transactional(readOnly = true)
    public List<CampaignDTO> getAllCampaigns(CurrentUser user) {
        requireManager(user);
        List<Campaign> list = user.isAdmin()
                ? campaignRepository.findAllByOrderByIdDesc()
                : campaignRepository.findByOwnerSubOrderByIdDesc(user.id());
        return list.stream().map(this::convertToDTO).toList();
    }

    @Transactional(readOnly = true)
    public CampaignDTO getCampaign(Long id, CurrentUser user) {
        return convertToDTO(load(id, user));
    }

    @Transactional
    public CampaignDTO createCampaign(CampaignDTO dto, CurrentUser user) {
        requireManager(user);
        validate(dto);
        Campaign campaign = convertToEntity(dto);
        campaign.setOwnerSub(user.id());
        campaign.setOwnerName(user.name());
        campaign.setShareToken(ShareTokens.generate());
        if (StringUtils.hasText(dto.getWebhookUrl())) {
            WebhookService.apply(webhookProps, campaign, dto.getWebhookUrl(), dto.getWebhookEvents());
        }
        campaign = campaignRepository.save(campaign);
        return convertToDTO(campaign);
    }

    /** Updates the campaign's own fields (questions are changed with {@link #replaceQuestions}). */
    @Transactional
    public CampaignDTO updateCampaign(Long id, CampaignDTO dto, CurrentUser user) {
        Campaign campaign = load(id, user);
        validateScalars(dto);
        if (campaign.isActive() && !dto.isActive()) {
            events.publishEvent(new CampaignClosed(campaign.getId()));
        }
        campaign.setTitle(dto.getTitle());
        campaign.setDescription(dto.getDescription());
        campaign.setStartDate(dto.getStartDate());
        campaign.setEndDate(dto.getEndDate());
        campaign.setActive(dto.isActive());
        campaign.setRequireLogin(dto.isRequireLogin());
        WebhookService.apply(webhookProps, campaign, dto.getWebhookUrl(), dto.getWebhookEvents());
        return convertToDTO(campaignRepository.save(campaign));
    }

    /** Replaces all questions; refused (409) once the campaign has responses. */
    @Transactional
    public CampaignDTO replaceQuestions(Long id, List<QuestionDTO> questions, CurrentUser user) {
        Campaign campaign = load(id, user);
        if (responseRepository.existsByQuestion_Campaign_Id(campaign.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Campaign already has responses; questions are locked");
        }
        validateQuestions(questions);
        campaign.getQuestions().clear();
        campaignRepository.flush();
        for (Question q : convertToEntity(questions, campaign)) {
            q.setId(null);
            campaign.getQuestions().add(q);
        }
        return convertToDTO(campaignRepository.save(campaign));
    }

    @Transactional
    public void deleteCampaign(Long id, CurrentUser user) {
        Campaign campaign = load(id, user);
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an admin may delete campaigns");
        }
        responseRepository.deleteByCampaignId(campaign.getId());
        webhookDeliveries.deleteByCampaignId(campaign.getId());
        campaignRepository.delete(campaign);
    }

    /** New share token; the old link stops working (404). */
    @Transactional
    public CampaignDTO rotateShareToken(Long id, CurrentUser user) {
        Campaign campaign = load(id, user);
        campaign.setShareToken(ShareTokens.generate());
        return convertToDTO(campaignRepository.save(campaign));
    }

    /** Close (open=false) or reopen the campaign; a closed campaign's link answers 410. */
    @Transactional
    public CampaignDTO setOpen(Long id, boolean open, CurrentUser user) {
        Campaign campaign = load(id, user);
        if (campaign.isActive() && !open) {
            events.publishEvent(new CampaignClosed(campaign.getId()));
        }
        campaign.setActive(open);
        return convertToDTO(campaignRepository.save(campaign));
    }

    @Transactional(readOnly = true)
    public CampaignResultsDTO getResults(Long id, CurrentUser user) {
        Campaign campaign = load(id, user);
        CampaignResultsDTO out = new CampaignResultsDTO();
        out.setCampaignId(campaign.getId());
        out.setTitle(campaign.getTitle());
        out.setSubmissions(responseRepository.countSubmissions(campaign.getId()));
        Map<Long, CampaignResultsDTO.QuestionResult> byQuestion = new LinkedHashMap<>();
        for (Question q : campaign.getQuestions()) {
            CampaignResultsDTO.QuestionResult qr = new CampaignResultsDTO.QuestionResult();
            qr.setQuestionId(q.getId());
            qr.setText(q.getText());
            qr.setType(q.getType());
            for (AnswerOption o : q.getOptions()) {
                qr.getOptions().add(new CampaignResultsDTO.OptionCount(o.getId(), o.getText(), 0));
            }
            byQuestion.put(q.getId(), qr);
            out.getQuestions().add(qr);
        }
        for (UserResponse r : responseRepository.findByQuestion_Campaign_Id(campaign.getId())) {
            CampaignResultsDTO.QuestionResult qr = byQuestion.get(r.getQuestion().getId());
            if (qr == null) {
                continue;
            }
            if (r.getSelectedOption() != null) {
                Long oid = r.getSelectedOption().getId();
                qr.getOptions().stream().filter(o -> Objects.equals(o.getOptionId(), oid))
                        .findFirst().ifPresent(o -> o.setCount(o.getCount() + 1));
            } else if (StringUtils.hasText(r.getTextResponse())) {
                qr.getTextAnswers().add(r.getTextResponse());
            }
        }
        return out;
    }

    /** Loads a campaign the user may manage, else 404. */
    @Transactional(readOnly = true)
    public Campaign load(Long id, CurrentUser user) {
        requireManager(user);
        Campaign c = campaignRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
        if (!user.isAdmin() && !Objects.equals(c.getOwnerSub(), user.id())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found");
        }
        return c;
    }

    public String shareUrl(String token) {
        if (token == null) {
            return null;
        }
        return publicUrl + "/r/" + token;
    }

    /** Gives legacy/seeded campaigns without a token one (run at startup). */
    @Transactional
    public int backfillShareTokens() {
        List<Campaign> missing = campaignRepository.findByShareTokenIsNull();
        missing.forEach(c -> c.setShareToken(ShareTokens.generate()));
        campaignRepository.saveAll(missing);
        return missing.size();
    }

    private static void requireManager(CurrentUser user) {
        if (user == null || !user.canManageCampaigns()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admin or creator role required");
        }
    }

    private static void validate(CampaignDTO dto) {
        validateScalars(dto);
        validateQuestions(dto.getQuestions());
    }

    private static void validateScalars(CampaignDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getTitle()) || dto.getTitle().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required (max 255 chars)");
        }
        if (dto.getDescription() != null && dto.getDescription().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "description max 255 chars");
        }
    }

    private static void validateQuestions(List<QuestionDTO> questions) {
        if (questions == null) {
            return;
        }
        if (questions.size() > MAX_QUESTIONS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "too many questions");
        }
        for (QuestionDTO q : questions) {
            if (q == null || !StringUtils.hasText(q.getText()) || q.getText().length() > 255) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question text is required (max 255 chars)");
            }
            if (q.getOptions() != null) {
                if (q.getOptions().size() > MAX_OPTIONS) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "too many options");
                }
                for (AnswerOptionDTO o : q.getOptions()) {
                    if (o == null || o.getText() == null || o.getText().length() > 255) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "option text max 255 chars");
                    }
                }
            }
        }
    }

    public CampaignDTO convertToDTO(Campaign campaign) {
        CampaignDTO dto = new CampaignDTO();
        dto.setId(campaign.getId());
        dto.setTitle(campaign.getTitle());
        dto.setDescription(campaign.getDescription());
        dto.setStartDate(campaign.getStartDate());
        dto.setEndDate(campaign.getEndDate());
        dto.setActive(campaign.isActive());
        dto.setRequireLogin(campaign.isRequireLogin());
        dto.setShareToken(campaign.getShareToken());
        dto.setShareUrl(shareUrl(campaign.getShareToken()));
        dto.setAcceptingResponses(campaign.isAcceptingResponses(LocalDateTime.now()));
        dto.setOwnerName(campaign.getOwnerName());
        dto.setWebhookUrl(campaign.getWebhookUrl());
        dto.setWebhookEvents(campaign.getWebhookUrl() == null ? null : WebhookService.events(campaign));
        if (campaign.getId() != null) {
            dto.setResponseCount(responseRepository.countSubmissions(campaign.getId()));
        }
        List<QuestionDTO> questionDTOS = new ArrayList<>();
        for (Question question : campaign.getQuestions()) {
            QuestionDTO questionDTO = new QuestionDTO();

            List<AnswerOptionDTO> answerOptionDTOS = new ArrayList<>();
            for (AnswerOption answerOption : question.getOptions()) {
                AnswerOptionDTO answerOptionDTO = new AnswerOptionDTO();
                answerOptionDTO.setId(answerOption.getId());
                answerOptionDTO.setOrderIndex(answerOption.getOrderIndex());
                answerOptionDTO.setQuestionId(question.getId());
                answerOptionDTO.setText(answerOption.getText());
                answerOptionDTOS.add(answerOptionDTO);
            }
            questionDTO.setOptions(answerOptionDTOS);
            questionDTO.setId(question.getId());
            questionDTO.setType(question.getType());
            questionDTO.setText(question.getText());
            questionDTO.setRequired(question.isRequired());
            questionDTO.setCampaignId(campaign.getId());
            questionDTO.setOrderIndex(question.getOrderIndex());
            questionDTOS.add(questionDTO);
        }
        dto.setQuestions(questionDTOS);
        return dto;
    }

    private Campaign convertToEntity(CampaignDTO dto) {
        Campaign campaign = new Campaign();

        campaign.setQuestions(convertToEntity(dto.getQuestions(), campaign));
        campaign.setTitle(dto.getTitle());
        campaign.setDescription(dto.getDescription());
        campaign.setStartDate(dto.getStartDate());
        campaign.setEndDate(dto.getEndDate());
        campaign.setActive(dto.isActive());
        campaign.setRequireLogin(dto.isRequireLogin());
        return campaign;
    }

    private List<Question> convertToEntity(List<QuestionDTO> questions, Campaign campaign) {
        List<Question> questionList = new ArrayList<>();
        if (questions == null) {
            return questionList;
        }
        for (QuestionDTO questionDTO : questions) {
            Question question = new Question();
            question.setCampaign(campaign);
            // A new campaign's questions are always new entities: ignore client-sent ids
            // (id == 0 or a real id from another campaign) so Hibernate assigns fresh ones.
            question.setText(questionDTO.getText());
            question.setType(questionDTO.getType());
            question.setOrderIndex(questionDTO.getOrderIndex());
            List<AnswerOption> answerOptions = new ArrayList<>();
            if (questionDTO.getOptions() != null) {
                for (AnswerOptionDTO answerOptionDTO : questionDTO.getOptions()) {
                    AnswerOption answerOption = new AnswerOption();
                    answerOption.setQuestion(question);
                    answerOption.setText(answerOptionDTO.getText());
                    answerOption.setOrderIndex(answerOptionDTO.getOrderIndex());
                    answerOptions.add(answerOption);
                }
            }
            question.setOptions(answerOptions);
            question.setRequired(questionDTO.isRequired());
            questionList.add(question);
        }
        return questionList;
    }
}
