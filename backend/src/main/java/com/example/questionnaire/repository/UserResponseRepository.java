package com.example.questionnaire.repository;

import com.example.questionnaire.model.UserResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserResponseRepository extends JpaRepository<UserResponse, Long> {
    List<UserResponse> findByUserId(String userId);
    List<UserResponse> findByQuestionId(Long questionId);
    List<UserResponse> findByUserIdAndQuestionIdOrderBySubmissionDateDesc(String userId, Long questionId);
    List<UserResponse> findByUserIdAndQuestion_Campaign_IdOrderBySubmissionDateDesc(String userId, Long campaignId);
    List<UserResponse> findByQuestion_Campaign_Id(Long campaignId);

    boolean existsByUserIdAndQuestion_Campaign_Id(String userId, Long campaignId);

    boolean existsByQuestion_Campaign_Id(Long campaignId);

    @Query("select count(distinct r.userId) from UserResponse r where r.question.campaign.id = :campaignId")
    long countSubmissions(@Param("campaignId") Long campaignId);

    @Modifying
    @Query("delete from UserResponse r where r.question.id in (select q.id from Question q where q.campaign.id = :campaignId)")
    int deleteByCampaignId(@Param("campaignId") Long campaignId);

    @Modifying
    @Query("delete from UserResponse r where r.question.id = :questionId")
    int deleteByQuestionId(@Param("questionId") Long questionId);
}
