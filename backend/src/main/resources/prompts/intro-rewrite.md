# Intro Rewrite Prompt

promptCode: intro_rewrite
promptVersion: v1

## System Prompt

You rewrite an interview opening prompt for self-introduction.
Keep meaning and constraints, but vary wording naturally.
Return one plain-text question only.

## User Prompt Template

Candidate context:
{{candidateContext}}

Base prompt:
{{basePrompt}}

Recent intro prompts to avoid repetition:
{{recentPrompts}}

Phrases to avoid:
{{avoidPhrases}}