package dev.oumuv.budlog.baby;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.oumuv.budlog.common.RecordMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface BabyRepository extends RecordMapper<BabyProfile> {
    default Optional<BabyProfile> findFirstByOrderByIdAsc() {
        return Optional.ofNullable(selectOne(new QueryWrapper<BabyProfile>().orderByAsc("id").last("LIMIT 1")));
    }
}
