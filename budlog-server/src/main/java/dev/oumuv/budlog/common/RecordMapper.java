package dev.oumuv.budlog.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Shared mechanics only; each business repository owns its query predicates. */
public interface RecordMapper<T extends AuditedEntity> extends BaseMapper<T> {
    default T save(T record) {
        int changed = record.getId() == null ? insert(record) : updateById(record);
        if (changed != 1) {
            throw new IllegalStateException("Expected one row when saving " + record.getClass().getSimpleName());
        }
        return record;
    }

    default Optional<T> findByIdAndDeletedAtIsNull(Long id) {
        return Optional.ofNullable(selectOne(new QueryWrapper<T>().eq("id", id).isNull("deleted_at")));
    }

    /** Deliberately includes deleted rows: request IDs must never be reused. */
    default Optional<T> findByClientRequestId(UUID id) {
        return Optional.ofNullable(selectOne(new QueryWrapper<T>().eq("client_request_id", id)));
    }

    default QueryWrapper<T> live(Long babyId) {
        return new QueryWrapper<T>().eq("baby_id", babyId).isNull("deleted_at");
    }

    default QueryWrapper<T> between(Long babyId, String column, Object from, Object to) {
        QueryWrapper<T> query = live(babyId);
        if (from != null) query.ge(column, from);
        if (to != null) query.lt(column, to);
        return query;
    }

    default RecordPage<T> page(QueryWrapper<T> query, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw BusinessException.validation("分页参数无效，size 须为 1 至 100，偏移不得溢出");
        }
        QueryWrapper<T> countQuery = query.clone();
        countQuery.getExpression().getOrderBy().clear();
        long total = selectCount(countQuery);
        // page and size are validated integers, never untrusted SQL fragments.
        List<T> records = selectList(query.last("LIMIT " + size + " OFFSET " + ((long) page * size)));
        return new RecordPage<T>(records, page, size, total);
    }
}
