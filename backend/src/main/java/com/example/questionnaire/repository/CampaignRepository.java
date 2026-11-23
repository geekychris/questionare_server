package com.example.questionnaire.repository;

import com.example.questionnaire.model.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    Optional<Campaign> findByShareToken(String shareToken);

    List<Campaign> findByOwnerSubOrderByIdDesc(String ownerSub);

    List<Campaign> findAllByOrderByIdDesc();

    List<Campaign> findByShareTokenIsNull();
}
