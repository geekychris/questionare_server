import com.example.questionnaire.dto.QuestionDTO;
import com.example.questionnaire.dto.AnswerOptionDTO;
import com.example.questionnaire.model.Question;
import com.example.questionnaire.model.AnswerOption;
import com.example.questionnaire.repository.QuestionRepository;
    }

    @Transactional
    public AnswerOptionDTO addAnswerOption(Long questionId, AnswerOptionDTO optionDTO) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        AnswerOption option = new AnswerOption();
        option.setText(optionDTO.getText());
        option.setOrderIndex(optionDTO.getOrderIndex());
        option.setQuestion(question);

        question.getOptions().add(option);
        questionRepository.save(question);

        return convertToDTO(option);
    }

    @Transactional
    public void deleteQuestion(Long id) {
        questionRepository.deleteById(id);
    }

    private QuestionDTO convertToDTO(Question question) {
        dto.setType(question.getType());
        dto.setRequired(question.isRequired());
        dto.setOrderIndex(question.getOrderIndex());
        dto.setOptions(question.getOptions().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList()));
        return dto;
    }

    private AnswerOptionDTO convertToDTO(AnswerOption option) {
        AnswerOptionDTO dto = new AnswerOptionDTO();
        dto.setId(option.getId());
        dto.setText(option.getText());
        dto.setOrderIndex(option.getOrderIndex());
        return dto;
    }

    private Question convertToEntity(QuestionDTO dto) {
