package dev.oumuv.budlog.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Configuration
@MapperScan(basePackages = "dev.oumuv.budlog", annotationClass = Mapper.class)
public class PersistenceConfig implements MetaObjectHandler {
    private final Clock clock;

    public PersistenceConfig(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        strictInsertFill(metaObject, "createdAt", OffsetDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", OffsetDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        setFieldValByName("updatedAt", OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC), metaObject);
    }
}
