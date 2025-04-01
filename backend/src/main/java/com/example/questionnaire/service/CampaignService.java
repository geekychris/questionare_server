package com.example.questionnaire.service;

import com.example.questionnaire.dto.CampaignDTO;
import com.example.questionnaire.model.Campaign;
import com.example.questionnaire.repository.CampaignRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CampaignService {
    @Autowired
    private CampaignRepository campaignRepository;

    @Transactional(readOnly = true)
    public List<CampaignDTO> getAllCampaigns() {
        return campaignRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CampaignDTO getCampaign(Long id) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found"));
        return convertToDTO(campaign);
    }

    @Transactional
    public CampaignDTO createCampaign(CampaignDTO campaignDTO) {
        Campaign campaign = convertToEntity(campaignDTO);
        campaign = campaignRepository.save(campaign);
        return convertToDTO(campaign);
    }

    @Transactional
    public CampaignDTO updateCampaign(Long id, CampaignDTO campaignDTO) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found"));
        
        campaign.setTitle(campaignDTO.getTitle());
        campaign.setDescription(campaignDTO.getDescription());
        campaign.setStartDate(campaignDTO.getStartDate());
        campaign.setEndDate(campaignDTO.getEndDate());
        campaign.setActive(campaignDTO.isActive());
        
        campaign = campaignRepository.save(campaign);
        return convertToDTO(campaign);
    }

    @Transactional
    public void deleteCampaign(Long id) {
        campaignRepository.deleteById(id);
    }

    private CampaignDTO convertToDTO(Campaign campaign) {
        CampaignDTO dto = new CampaignDTO();
        dto.setId(campaign.getId());
        dto.setTitle(campaign.getTitle());
        dto.setDescription(campaign.getDescription());
        dto.setStartDate(campaign.getStartDate());
        dto.setEndDate(campaign.getEndDate());
        dto.setActive(campaign.isActive());
        return dto;
    }

    private Campaign convertToEntity(CampaignDTO dto) {
        Campaign campaign = new Campaign();
        campaign.setTitle(dto.getTitle());
        campaign.setDescription(dto.getDescription());
        campaign.setStartDate(dto.getStartDate());
        campaign.setEndDate(dto.getEndDate());
        campaign.setActive(dto.isActive());
        return campaign;
    }
}

