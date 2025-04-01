-- Create a campaign
INSERT INTO campaign (title, description, start_date, end_date, active)
VALUES ('Developer Experience Survey',
        'Help us improve our developer tools and processes',
       NOW(),
        DATE_ADD(NOW(), INTERVAL 1 MONTH),
        true);

-- Questions
-- Q1: Multiple choice
INSERT INTO question (text, type, required, order_index, campaign_id)
SELECT 'What is your primary programming language?', 'MULTIPLE_CHOICE', true, 1, id 
FROM campaign WHERE title = 'Developer Experience Survey';

INSERT INTO answer_option (text, order_index, question_id)
SELECT 'Java', 1, id FROM question WHERE text = 'What is your primary programming language?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'Python', 2, id FROM question WHERE text = 'What is your primary programming language?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'JavaScript', 3, id FROM question WHERE text = 'What is your primary programming language?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'C++', 4, id FROM question WHERE text = 'What is your primary programming language?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'Other', 5, id FROM question WHERE text = 'What is your primary programming language?';

-- Q2: Text
INSERT INTO question (text, type, required, order_index, campaign_id)
SELECT 'What IDE or text editor do you primarily use?', 'TEXT', true, 2, id 
FROM campaign WHERE title = 'Developer Experience Survey';

-- Q3: Multiple choice
INSERT INTO question (text, type, required, order_index, campaign_id)
SELECT 'How many years of programming experience do you have?', 'MULTIPLE_CHOICE', true, 3, id 
FROM campaign WHERE title = 'Developer Experience Survey';

INSERT INTO answer_option (text, order_index, question_id)
SELECT '0-2 years', 1, id FROM question WHERE text = 'How many years of programming experience do you have?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT '3-5 years', 2, id FROM question WHERE text = 'How many years of programming experience do you have?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT '6-10 years', 3, id FROM question WHERE text = 'How many years of programming experience do you have?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT '10+ years', 4, id FROM question WHERE text = 'How many years of programming experience do you have?';

-- Q4: Multiple choice
INSERT INTO question (text, type, required, order_index, campaign_id)
SELECT 'How satisfied are you with our current development tools?', 'MULTIPLE_CHOICE', true, 4, id 
FROM campaign WHERE title = 'Developer Experience Survey';

INSERT INTO answer_option (text, order_index, question_id)
SELECT 'Very satisfied', 1, id FROM question WHERE text = 'How satisfied are you with our current development tools?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'Satisfied', 2, id FROM question WHERE text = 'How satisfied are you with our current development tools?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'Neutral', 3, id FROM question WHERE text = 'How satisfied are you with our current development tools?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'Dissatisfied', 4, id FROM question WHERE text = 'How satisfied are you with our current development tools?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'Very dissatisfied', 5, id FROM question WHERE text = 'How satisfied are you with our current development tools?';

-- Q5: Text
INSERT INTO question (text, type, required, order_index, campaign_id)
SELECT 'What improvements would you suggest for our development workflow?', 'TEXT', false, 5, id 
FROM campaign WHERE title = 'Developer Experience Survey';

-- Q6: Multiple choice
INSERT INTO question (text, type, required, order_index, campaign_id)
SELECT 'How often do you commit code?', 'MULTIPLE_CHOICE', true, 6, id 
FROM campaign WHERE title = 'Developer Experience Survey';

INSERT INTO answer_option (text, order_index, question_id)
SELECT 'Multiple times per day', 1, id FROM question WHERE text = 'How often do you commit code?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'Once per day', 2, id FROM question WHERE text = 'How often do you commit code?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'A few times per week', 3, id FROM question WHERE text = 'How often do you commit code?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'Once per week', 4, id FROM question WHERE text = 'How often do you commit code?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'Less frequently', 5, id FROM question WHERE text = 'How often do you commit code?';

-- Q7: Text
INSERT INTO question (text, type, required, order_index, campaign_id)
SELECT 'What is your biggest challenge in the current development process?', 'TEXT', true, 7, id 
FROM campaign WHERE title = 'Developer Experience Survey';

-- Q8: Multiple choice
INSERT INTO question (text, type, required, order_index, campaign_id)
SELECT 'Which testing framework do you prefer?', 'MULTIPLE_CHOICE', true, 8, id 
FROM campaign WHERE title = 'Developer Experience Survey';

INSERT INTO answer_option (text, order_index, question_id)
SELECT 'JUnit', 1, id FROM question WHERE text = 'Which testing framework do you prefer?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'TestNG', 2, id FROM question WHERE text = 'Which testing framework do you prefer?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'PyTest', 3, id FROM question WHERE text = 'Which testing framework do you prefer?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'Jest', 4, id FROM question WHERE text = 'Which testing framework do you prefer?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'Other', 5, id FROM question WHERE text = 'Which testing framework do you prefer?';

-- Q9: Multiple choice
INSERT INTO question (text, type, required, order_index, campaign_id)
SELECT 'How would you rate our code review process?', 'MULTIPLE_CHOICE', true, 9, id 
FROM campaign WHERE title = 'Developer Experience Survey';

INSERT INTO answer_option (text, order_index, question_id)
SELECT 'Excellent', 1, id FROM question WHERE text = 'How would you rate our code review process?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'Good', 2, id FROM question WHERE text = 'How would you rate our code review process?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'Fair', 3, id FROM question WHERE text = 'How would you rate our code review process?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'Poor', 4, id FROM question WHERE text = 'How would you rate our code review process?';
INSERT INTO answer_option (text, order_index, question_id)
SELECT 'Very poor', 5, id FROM question WHERE text = 'How would you rate our code review process?';

-- Q10: Text
INSERT INTO question (text, type, required, order_index, campaign_id)
SELECT 'What additional tools or resources would help improve your productivity?', 'TEXT', false, 10, id 
FROM campaign WHERE title = 'Developer Experience Survey';
