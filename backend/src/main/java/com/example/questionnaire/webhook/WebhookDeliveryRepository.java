package com.example.questionnaire.webhook;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, String> {

    List<WebhookDelivery> findByCampaignIdOrderByCreatedAtDesc(Long campaignId, Pageable page);

    @Query("select d from WebhookDelivery d where d.status = :status and d.nextAttemptAt <= :now order by d.nextAttemptAt")
    List<WebhookDelivery> findDue(@Param("status") WebhookDelivery.Status status, @Param("now") LocalDateTime now, Pageable page);

    @Modifying
    @Query("delete from WebhookDelivery d where d.campaignId = :campaignId")
    int deleteByCampaignId(@Param("campaignId") Long campaignId);
}
