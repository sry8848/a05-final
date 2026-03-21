import test from 'node:test'
import assert from 'node:assert/strict'

import { buildInterviewCreatePayload } from '../src/utils/interviewSessionPayload.js'

const roleMap = {
  frontend: 'FRONTEND'
}

const experienceMap = {
  intern: 'INTERN'
}

test('buildInterviewCreatePayload should omit single-question fields for normal interviews', () => {
  const payload = buildInterviewCreatePayload({
    config: {
      jobType: 'frontend',
      experience: 'intern',
      interviewMode: 'practice',
      jobDescription: '',
      resumeId: 'default',
      knowledgePoints: ['Vue', '工程化']
    },
    roleMap,
    experienceMap
  })

  assert.equal(payload.targetRole, 'FRONTEND')
  assert.equal(payload.experienceLevel, 'INTERN')
  assert.equal(payload.mode, 'practice')
  assert.equal(payload.focusTopics, 'Vue,工程化')
  assert.equal('singleQuestionStem' in payload, false)
  assert.equal('singleQuestionType' in payload, false)
  assert.equal('singleQuestionDomainName' in payload, false)
  assert.equal('singleQuestionExpectedPoints' in payload, false)
  assert.equal('maxQuestions' in payload, false)
})
