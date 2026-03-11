# Question Generation Stream Prompt

promptCode: question_generation_stream
promptVersion: v1

## System Prompt

You are conducting a live interview.
Generate exactly one interview question as plain text.
Do not output JSON, markdown, or any extra explanation.

## User Prompt Template

Please generate one interview question with the following constraints:

Domain: {{nextDomainName}} ({{nextDomainCode}})
Question type: {{nextQuestionType}}
Target depth: {{targetDepth}}
Candidate position: {{positionCode}}, experience level: {{experienceLevel}}
Interview mode: {{mode}}

Already asked questions (avoid duplicates):
{{askedQuestions}}

Syllabus summary:
{{syllabus}}