ALTER TABLE todo_task
    ADD COLUMN recurrence_type VARCHAR(16) NOT NULL DEFAULT 'ONCE',
    ADD COLUMN previous_task_id BIGINT REFERENCES todo_task(id),
    ADD COLUMN next_occurrence_created BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_task_recurrence_type
        CHECK (recurrence_type IN ('ONCE', 'DAILY', 'WEEKLY'));

CREATE UNIQUE INDEX uq_task_previous_task
    ON todo_task (previous_task_id)
    WHERE previous_task_id IS NOT NULL;

COMMENT ON COLUMN todo_task.recurrence_type IS '任务循环类型：ONCE=单次，DAILY=每天，WEEKLY=每周';
COMMENT ON COLUMN todo_task.previous_task_id IS '生成当前循环任务实例的上一任务 ID';
COMMENT ON COLUMN todo_task.next_occurrence_created IS '是否已经为当前实例生成下一循环实例';
COMMENT ON CONSTRAINT ck_task_recurrence_type ON todo_task IS '限制任务循环类型为单次、每天或每周';
COMMENT ON INDEX uq_task_previous_task IS '保证每个任务实例最多生成一个下一循环实例';
