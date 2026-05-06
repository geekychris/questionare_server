import React, { useState } from 'react';
import { 
  Box, 
  Button, 
  Card, 
  CardContent, 
  Divider, 
  FormControl, 
  FormHelperText, 
  Grid, 
  IconButton, 
  InputLabel, 
  MenuItem, 
  Paper, 
  Select, 
  TextField, 
  Typography,
  FormControlLabel,
  Checkbox
} from '@mui/material';
import { GridTypeMap } from '@mui/material/Grid';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import { Formik, Form, FieldArray, Field, ErrorMessage, FormikHelpers, FormikErrors, FormikTouched } from 'formik';
import * as Yup from 'yup';
import { Campaign, Question, QuestionType } from '../../types';
import { useNavigate } from 'react-router-dom';

interface CampaignFormProps {
  initialValues?: Campaign;
  onSubmit: (values: Campaign) => Promise<void>;
  isEditMode?: boolean;
  isSubmitting?: boolean;
}

// Define FormValues type to match Campaign structure
interface FormValues extends Campaign {
  questions: Array<Omit<Question, 'campaignId'> & { options: string[] }>;
}

const defaultInitialValues: FormValues = {
  id: 0,
  title: '',
  description: '',
  active: true,
  questions: [],
  startDate: new Date().toISOString(),
  endDate: new Date().toISOString(),
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString()
};

const CampaignForm: React.FC<CampaignFormProps> = ({ 
  initialValues = defaultInitialValues, 
  onSubmit,
  isEditMode = false
}) => {
  const navigate = useNavigate();
  const [submitting, setSubmitting] = useState(false);

  const validationSchema = Yup.object({
    title: Yup.string().required('Title is required'),
    description: Yup.string().required('Description is required'),
    questions: Yup.array().of(
      Yup.object().shape({
        text: Yup.string().required('Question text is required'),
        type: Yup.string().required('Question type is required'),
        required: Yup.boolean(),
        order: Yup.number().required(),
        options: Yup.array().when('type', {
          is: QuestionType.MULTIPLE_CHOICE,
          then: Yup.array()
            .of(Yup.string().required('Option text is required'))
            .min(2, 'At least 2 options are required')
            .required('Options are required for multiple choice questions'),
          otherwise: Yup.array().of(Yup.string())
        })
      })
    ).min(1, 'At least one question is required')
  });

  const handleSubmit = async (
    values: FormValues, 
    { setSubmitting, resetForm }: FormikHelpers<FormValues>
  ) => {
    try {
      await onSubmit(values);
      resetForm();
      navigate('/campaigns');
    } catch (error) {
      console.error('Error submitting form:', error);
    } finally {
      setSubmitting(false);
    }
  };

  const getNextQuestionOrder = (questions: Question[]): number => {
    if (questions.length === 0) return 1;
    return Math.max(...questions.map(q => q.order)) + 1;
  };

  return (
    <Box sx={{ p: 3 }}>
      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h5" gutterBottom>
          {isEditMode ? 'Edit Campaign' : 'Create New Campaign'}
        </Typography>
        
        <Formik
          initialValues={initialValues}
          validationSchema={validationSchema}
          onSubmit={handleSubmit}
        >
          {({ values, errors, touched, handleChange, handleBlur, isSubmitting, setFieldValue }: {
            values: FormValues;
            errors: FormikErrors<FormValues>;
            touched: FormikTouched<FormValues>;
            handleChange: any;
            handleBlur: any;
            isSubmitting: boolean;
            setFieldValue: (field: string, value: any) => void;
          }) => (
            <Form>
              <Grid container spacing={3} component="div">
                <Grid item xs={12} component="div">
                  <TextField
                    fullWidth
                    id="title"
                    name="title"
                    label="Campaign Title"
                    value={values.title}
                    onChange={handleChange}
                    onBlur={handleBlur}
                    error={touched.title && Boolean(errors.title)}
                    helperText={touched.title && errors.title}
                    variant="outlined"
                    margin="normal"
                  />
                </Grid>
                
                <Grid item xs={12} component="div">
                  <TextField
                    fullWidth
                    id="description"
                    name="description"
                    label="Campaign Description"
                    value={values.description}
                    onChange={handleChange}
                    onBlur={handleBlur}
                    error={touched.description && Boolean(errors.description)}
                    helperText={touched.description && errors.description}
                    variant="outlined"
                    margin="normal"
                    multiline
                    rows={3}
                  />
                </Grid>
                
                <Grid item xs={12} component="div">
                  <FormControlLabel
                    control={
                      <Checkbox
                        name="active"
                        checked={values.active}
                        onChange={handleChange}
                      />
                    }
                    label="Active Campaign"
                  />
                </Grid>
              </Grid>
              
              <Divider sx={{ my: 3 }} />
              
              <Typography variant="h6" gutterBottom>
                Questions
              </Typography>
              
              {/* Questions FieldArray */}
              <FieldArray name="questions">
                {({ insert, remove, push }) => (
                  <Box>
                    {values.questions.length > 0 &&
                      values.questions.map((question, index) => (
                        <Card key={index} sx={{ mb: 3 }}>
                          <CardContent>
                            <Grid container spacing={2} component="div">
                              <Grid item xs={11} component="div">
                                <Typography variant="subtitle1" fontWeight="bold">
                                  Question #{index + 1}
                                </Typography>
                              </Grid>
                              <Grid item xs={1} sx={{ textAlign: 'right' }} component="div">
                                <IconButton 
                                  onClick={() => remove(index)}
                                  color="error"
                                  size="small"
                                >
                                  <DeleteIcon />
                                </IconButton>
                              </Grid>
                              
                              <Grid item xs={12} component="div">
                                <TextField
                                  fullWidth
                                  name={`questions.${index}.text`}
                                  label="Question Text"
                                  value={question.text}
                                  onChange={handleChange}
                                  onBlur={handleBlur}
                                  error={
                                    touched.questions?.[index] && 
                                    (touched.questions[index] as FormikTouched<Question>).text && 
                                    Boolean(errors.questions?.[index] && 
                                    (errors.questions[index] as FormikErrors<Question>).text)
                                  }
                                  helperText={
                                    touched.questions?.[index] && 
                                    (touched.questions[index] as FormikTouched<Question>).text && 
                                    errors.questions?.[index] && 
                                    (errors.questions[index] as FormikErrors<Question>).text
                                  }
                                  variant="outlined"
                                />
                              </Grid>
                              
                              <Grid item xs={6} component="div">
                                <FormControl fullWidth error={
                                  touched.questions?.[index] && 
                                  (touched.questions[index] as FormikTouched<Question>).type && 
                                  Boolean(errors.questions?.[index] && 
                                  (errors.questions[index] as FormikErrors<Question>).type)
                                }>
                                  <InputLabel id={`questions.${index}.type-label`}>
                                    Question Type
                                  </InputLabel>
                                  <Select
                                    labelId={`questions.${index}.type-label`}
                                    id={`questions.${index}.type`}
                                    name={`questions.${index}.type`}
                                    value={question.type}
                                    label="Question Type"
                                    onChange={handleChange}
                                    onBlur={handleBlur}
                                  >
                                    <MenuItem value={QuestionType.TEXT}>Text Input</MenuItem>
                                    <MenuItem value={QuestionType.MULTIPLE_CHOICE}>Multiple Choice</MenuItem>
                                  </Select>
                                  {touched.questions?.[index] && 
                                    (touched.questions[index] as FormikTouched<Question>).type && 
                                    errors.questions?.[index] && 
                                    (errors.questions[index] as FormikErrors<Question>).type && (
                                    <FormHelperText>
                                      {(errors.questions[index] as FormikErrors<Question>).type as string}
                                    </FormHelperText>
                                  )}
                                </FormControl>
                              </Grid>
                              
                              <Grid item xs={6} component="div">
                                <FormControlLabel
                                  control={
                                    <Checkbox
                                      name={`questions.${index}.required`}
                                      checked={question.required}
                                      onChange={handleChange}
                                    />
                                  }
                                  label="Required Question"
                                />
                              </Grid>
                              
                              {/* Dynamic fields based on question type */}
                              {question.type === QuestionType.MULTIPLE_CHOICE && (
                                <Grid item xs={12} component="div">
                                  <Typography variant="subtitle2" sx={{ mb: 1 }}>
                                    Answer Options
                                  </Typography>
                                  
                                  <FieldArray name={`questions.${index}.options`}>
                                    {({ remove: removeOption, push: pushOption }) => (
                                      <>
                                        {question.options && question.options.map((option, optionIndex) => (
                                          <Box key={optionIndex} sx={{ display: 'flex', mb: 1 }}>
                                            <TextField
                                              fullWidth
                                              name={`questions.${index}.options.${optionIndex}`}
                                              label={`Option ${optionIndex + 1}`}
                                              value={option}
                                              onChange={handleChange}
                                              onBlur={handleBlur}
                                              error={
                                                touched.questions?.[index] && 
                                                (touched.questions[index] as any).options && 
                                                (touched.questions[index] as any).options[optionIndex] && 
                                                Boolean(
                                                  errors.questions?.[index] && 
                                                  typeof (errors.questions[index] as any).options === 'object' && 
                                                  (errors.questions[index] as any).options?.[optionIndex]
                                                )
                                              }
                                              helperText={
                                                touched.questions?.[index] && 
                                                (touched.questions[index] as any).options && 
                                                (touched.questions[index] as any).options[optionIndex] && 
                                                (errors.questions?.[index] && 
                                                typeof (errors.questions[index] as any).options === 'object' ? 
                                                  (errors.questions[index] as any).options?.[optionIndex] : '')
                                              }
                                              variant="outlined"
                                              size="small"
                                            />
                                            <IconButton 
                                              onClick={() => removeOption(optionIndex)}
                                              color="error"
                                              sx={{ ml: 1 }}
                                            >
                                              <DeleteIcon />
                                            </IconButton>
                                          </Box>
                                        ))}
                                        
                                        <Button
                                          startIcon={<AddIcon />}
                                          onClick={() => pushOption('')}
                                          variant="outlined"
                                          color="primary"
                                          size="small"
                                        >
                                          Add Option
                                        </Button>
                                        
                                        {/* Array-level validation error */}
                                        {errors.questions?.[index] && 
                                         typeof (errors.questions[index] as any).options === 'string' && (
                                          <FormHelperText error>
                                            {(errors.questions[index] as any).options as string}
                                          </FormHelperText>
                                        )}
                                      </>
                                    )}
                                  </FieldArray>
                                </Grid>
                              )}
                            </Grid>
                          </CardContent>
                        </Card>
                      ))}
                    
                    <Button
                      startIcon={<AddIcon />}
                      onClick={() => push({
                        text: '',
                        type: QuestionType.TEXT,
                        required: false,
                        order: getNextQuestionOrder(values.questions),
                        options: []
                      })}
                      variant="contained"
                      color="primary"
                      sx={{ mb: 2 }}
                    >
                      Add Question
                    </Button>
                    
                    {/* Array-level validation error */}
                    {typeof errors.questions === 'string' && (
                      <Typography color="error" variant="body2" sx={{ mb: 2 }}>
                        {errors.questions}
                      </Typography>
                    )}
                  </Box>
                )}
              </FieldArray>
              
              <Divider sx={{ my: 3 }} />
              
              <Box sx={{ display: 'flex', gap: 2, justifyContent: 'flex-end' }}>
                <Button
                  variant="outlined"
                  onClick={() => navigate('/campaigns')}
                >
                  Cancel
                </Button>
                <Button
                  type="submit"
                  variant="contained"
                  color="primary"
                  disabled={isSubmitting}
                >
                  {isEditMode ? 'Update Campaign' : 'Create Campaign'}
                </Button>
              </Box>
            </Form>
          )}
        </Formik>
      </Paper>
    </Box>
  );
};

export default CampaignForm;

