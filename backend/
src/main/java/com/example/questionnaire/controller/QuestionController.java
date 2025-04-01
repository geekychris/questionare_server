        return questionService.updateQuestion(id, questionDTO);
    }

    @PostMapping("/{questionId}/options")
    public AnswerOptionDTO addAnswerOption(
            @PathVariable Long questionId,
            @RequestBody AnswerOptionDTO optionDTO) {
        return questionService.addAnswerOption(questionId, optionDTO);
    }

    @DeleteMapping("/{id}")
    public void deleteQuestion(@PathVariable Long id) {
        questionService.deleteQuestion(id);
    }
}
