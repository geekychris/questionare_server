package com.example.questionnaire.controller;

import com.example.questionnaire.dto.CampaignDTO;
import com.example.questionnaire.dto.CampaignResultsDTO;
import com.example.questionnaire.dto.QuestionDTO;
import com.example.questionnaire.security.CurrentUser;
import com.example.questionnaire.service.CampaignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Campaign management API (roles admin/creator; see SecurityConfig). Every state-changing call
 * needs the CSRF token from {@code GET /api/csrf} in the {@code X-CSRF-TOKEN} header.
 */
@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {
    private static final Logger log = LoggerFactory.getLogger(CampaignController.class);

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @GetMapping
    public List<CampaignDTO> getAllCampaigns(Authentication auth) {
        return campaignService.getAllCampaigns(CurrentUser.from(auth));
    }

    @GetMapping("/{id}")
    public CampaignDTO getCampaign(@PathVariable Long id, Authentication auth) {
        return campaignService.getCampaign(id, CurrentUser.from(auth));
    }

    @PostMapping
    public CampaignDTO createCampaign(@RequestBody CampaignDTO campaignDTO, Authentication auth) {
        CurrentUser user = CurrentUser.from(auth);
        CampaignDTO saved = campaignService.createCampaign(campaignDTO, user);
        log.info("campaign {} created by {} ({} questions)", saved.getId(), user.name(), saved.getQuestions().size());
        return saved;
    }

    @PutMapping("/{id}")
    public CampaignDTO updateCampaign(@PathVariable Long id, @RequestBody CampaignDTO campaignDTO, Authentication auth) {
        return campaignService.updateCampaign(id, campaignDTO, CurrentUser.from(auth));
    }

    /** Replaces all questions (409 once there are responses). */
    @PutMapping("/{id}/questions")
    public CampaignDTO replaceQuestions(@PathVariable Long id, @RequestBody List<QuestionDTO> questions, Authentication auth) {
        return campaignService.replaceQuestions(id, questions, CurrentUser.from(auth));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCampaign(@PathVariable Long id, Authentication auth) {
        CurrentUser user = CurrentUser.from(auth);
        campaignService.deleteCampaign(id, user);
        log.info("campaign {} deleted by {}", id, user.name());
        return ResponseEntity.noContent().build();
    }

    /** The respondent link: {@code {token, url, open, requireLogin}}. */
    @GetMapping("/{id}/share")
    public Map<String, Object> getShare(@PathVariable Long id, Authentication auth) {
        return share(campaignService.getCampaign(id, CurrentUser.from(auth)));
    }

    /** New token; the old link answers 404 from now on. */
    @PostMapping("/{id}/share/rotate")
    public Map<String, Object> rotateShare(@PathVariable Long id, Authentication auth) {
        return share(campaignService.rotateShareToken(id, CurrentUser.from(auth)));
    }

    /** Stop accepting responses (the link answers 410). */
    @PostMapping("/{id}/share/close")
    public Map<String, Object> closeShare(@PathVariable Long id, Authentication auth) {
        return share(campaignService.setOpen(id, false, CurrentUser.from(auth)));
    }

    @PostMapping("/{id}/share/open")
    public Map<String, Object> openShare(@PathVariable Long id, Authentication auth) {
        return share(campaignService.setOpen(id, true, CurrentUser.from(auth)));
    }

    @GetMapping("/{id}/results")
    public CampaignResultsDTO results(@PathVariable Long id, Authentication auth) {
        return campaignService.getResults(id, CurrentUser.from(auth));
    }

    private static Map<String, Object> share(CampaignDTO c) {
        return Map.of(
                "token", c.getShareToken(),
                "url", c.getShareUrl(),
                "open", c.isAcceptingResponses(),
                "requireLogin", c.isRequireLogin());
    }
}
