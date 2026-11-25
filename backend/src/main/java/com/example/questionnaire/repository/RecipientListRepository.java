package com.example.questionnaire.repository;

import com.example.questionnaire.model.RecipientList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecipientListRepository extends JpaRepository<RecipientList, Long> {
    List<RecipientList> findByOwnerSubOrderByNameAsc(String ownerSub);

    List<RecipientList> findAllByOrderByNameAsc();

    Optional<RecipientList> findByOwnerSubAndName(String ownerSub, String name);
}
