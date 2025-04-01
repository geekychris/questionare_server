package com.example.questionnaire.controller;

import com.example.questionnaire.dto.CampaignDTO;
import com.example.questionnaire.service.CampaignService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {
    @Autowired
    private CampaignService campaignService;

    @GetMapping
    public List<CampaignDTO> getAllCampaigns() {
        return campaignService.getAllCampaigns();
    }

    @GetMapping("/{id}")
    public CampaignDTO getCampaign(@PathVariable Long id) {
        return campaignService.getCampaign(id);
    }

    @PostMapping
    public CampaignDTO createCampaign(@RequestBody CampaignDTO campaignDTO) {
        return campaignService.createCampaign(campaignDTO);
    }

    @PutMapping("/{id}")
    public CampaignDTO updateCampaign(@PathVariable Long id, @RequestBody CampaignDTO campaignDTO) {
        return campaignService.updateCampaign(id, campaignDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCampaign(@PathVariable Long id) {
        campaignService.deleteCampaign(id);
        return ResponseEntity.ok().build();
    }
}

