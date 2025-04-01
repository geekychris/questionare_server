#!/bin/bash

# Base URL for the API
BASE_URL="http://localhost:8080/api"

# Create campaign
echo "Creating campaign..."
CAMPAIGN_RESPONSE=$(curl -s -X POST "${BASE_URL}/campaigns" \
    -H "Content-Type: application/json" \
    -d '{
        "title": "Developer Experience Survey",
        "description": "Help us improve our developer tools and processes",
        "startDate": "2025-03-31T00:00:00Z",
        "endDate": "2025-04-30T23:59:59Z",
        "active": true
    }')
CAMPAIGN_ID=$(echo $CAMPAIGN_RESPONSE | jq -r '.id')
echo "Campaign created with ID: $CAMPAIGN_ID"

# Function to create a multiple choice question
create_multiple_choice_question() {
    local text="$1"
    local required="$2"
    local order="$3"
    local options=("${@:4}")
    
    # Create question
    echo "Creating question: $text"
    QUESTION_RESPONSE=$(curl -s -X POST "${BASE_URL}/questions" \
        -H "Content-Type: application/json" \
        -d "{
            \"text\": \"$text\",
            \"type\": \"MULTIPLE_CHOICE\",
            \"required\": $required,
            \"orderIndex\": $order,
            \"campaignId\": $CAMPAIGN_ID
        }")
    QUESTION_ID=$(echo $QUESTION_RESPONSE | jq -r '.id')
    
    # Create answer options
    local order_index=1
    for option in "${options[@]}"; do
        echo "Creating option: $option"
        curl -s -X POST "${BASE_URL}/questions/${QUESTION_ID}/options" \
            -H "Content-Type: application/json" \
            -d "{
                \"text\": \"$option\",
                \"orderIndex\": $order_index
            }"
        ((order_index++))
    done
}

# Function to create a text question
create_text_question() {
    local text="$1"
    local required="$2"
    local order="$3"
    
    echo "Creating question: $text"
    curl -s -X POST "${BASE_URL}/questions" \
        -H "Content-Type: application/json" \
        -d "{
            \"text\": \"$text\",
            \"type\": \"TEXT\",
            \"required\": $required,
            \"orderIndex\": $order,
            \"campaignId\": $CAMPAIGN_ID
        }"
}

# Q1: Programming Language
create_multiple_choice_question \
    "What is your primary programming language?" \
    true 1 \
    "Java" "Python" "JavaScript" "C++" "Other"

# Q2: IDE
create_text_question \
    "What IDE or text editor do you primarily use?" \
    true 2

# Q3: Experience
create_multiple_choice_question \
    "How many years of programming experience do you have?" \
    true 3 \
    "0-2 years" "3-5 years" "6-10 years" "10+ years"

# Q4: Tool Satisfaction
create_multiple_choice_question \
    "How satisfied are you with our current development tools?" \
    true 4 \
    "Very satisfied" "Satisfied" "Neutral" "Dissatisfied" "Very dissatisfied"

# Q5: Workflow Improvements
create_text_question \
    "What improvements would you suggest for our development workflow?" \
    false 5

# Q6: Commit Frequency
create_multiple_choice_question \
    "How often do you commit code?" \
    true 6 \
    "Multiple times per day" "Once per day" "A few times per week" "Once per week" "Less frequently"

# Q7: Development Challenges
create_text_question \
    "What is your biggest challenge in the current development process?" \
    true 7

# Q8: Testing Framework
create_multiple_choice_question \
    "Which testing framework do you prefer?" \
    true 8 \
    "JUnit" "TestNG" "PyTest" "Jest" "Other"

# Q9: Code Review
create_multiple_choice_question \
    "How would you rate our code review process?" \
    true 9 \
    "Excellent" "Good" "Fair" "Poor" "Very poor"

# Q10: Productivity Tools
create_text_question \
    "What additional tools or resources would help improve your productivity?" \
    false 10

echo "Questionnaire creation completed!"

