package com.circleguard.form.repository;

import com.circleguard.form.model.Questionnaire;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface QuestionnaireRepository extends JpaRepository<Questionnaire, UUID> {
    @EntityGraph(attributePaths = "questions")
    Optional<Questionnaire> findFirstByIsActiveTrueOrderByVersionDesc();
}
