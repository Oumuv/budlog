package dev.oumuv.budlog.setting;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface SettingRepository extends BaseMapper<AppSetting> {
    default Optional<AppSetting> findById(Short id) {
        return Optional.ofNullable(selectById(id));
    }

    default AppSetting save(AppSetting setting) {
        if (updateById(setting) != 1) {
            throw new IllegalStateException("Default app_setting row is missing");
        }
        return setting;
    }
}
