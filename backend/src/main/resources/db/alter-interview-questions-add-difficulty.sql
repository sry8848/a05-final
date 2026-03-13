ALTER TABLE interview_questions
    ADD COLUMN difficulty VARCHAR(16) NULL COMMENT '本题难度等级：L1~L5' AFTER target_depth;
