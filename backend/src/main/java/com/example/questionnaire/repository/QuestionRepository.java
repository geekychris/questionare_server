package com.example.questionnaire.repository;

import com.example.questionnaire.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByCampaignIdOrderByOrderIndexAsc(Long campaignId);
}

