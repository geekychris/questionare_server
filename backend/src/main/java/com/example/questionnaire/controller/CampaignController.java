package com.example.questionnaire.controller;

import com.example.questionnaire.dto.CampaignDTO;
import com.example.questionnaire.service.CampaignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {
    private static final Logger log = LoggerFactory.getLogger(CampaignController.class);

    @Autowired
    private CampaignService campaignService;

    @GetMapping
    public List<CampaignDTO> getAllCampaigns() {
        List<CampaignDTO> all = campaignService.getAllCampaigns();
        log.info("GET /api/campaigns -> {} campaigns", all.size());
        return all;
    }

    @GetMapping("/{id}")
    public CampaignDTO getCampaign(@PathVariable Long id) {
        log.info("GET /api/campaigns/{}", id);
        return campaignService.getCampaign(id);
    }

    @PostMapping
    public CampaignDTO createCampaign(@RequestBody CampaignDTO campaignDTO) {
        log.info(
            "POST /api/campaigns title='{}' active={} questions={}",
            campaignDTO.getTitle(),
            campaignDTO.isActive(),
            campaignDTO.getQuestions() == null ? 0 : campaignDTO.getQuestions().size()
        );
        CampaignDTO saved = campaignService.createCampaign(campaignDTO);
        log.info("POST /api/campaigns -> id={}", saved.getId());
        return saved;
    }

    @PutMapping("/{id}")
    public CampaignDTO updateCampaign(@PathVariable Long id, @RequestBody CampaignDTO campaignDTO) {
        log.info("PUT /api/campaigns/{} title='{}'", id, campaignDTO.getTitle());
        return campaignService.updateCampaign(id, campaignDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCampaign(@PathVariable Long id) {
        log.info("DELETE /api/campaigns/{}", id);
        campaignService.deleteCampaign(id);
        return ResponseEntity.ok().build();
    }
}

