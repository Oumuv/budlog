package dev.oumuv.budlog.common;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import dev.oumuv.budlog.config.PersistenceConfig;
import dev.oumuv.budlog.feeding.BreastSide;
import dev.oumuv.budlog.feeding.FeedingRecord;
import dev.oumuv.budlog.feeding.FeedingRepository;
import dev.oumuv.budlog.feeding.FeedingType;
import dev.oumuv.budlog.task.TaskRecurrenceType;
import dev.oumuv.budlog.task.TaskRepository;
import dev.oumuv.budlog.task.TaskStatus;
import dev.oumuv.budlog.task.TodoTask;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SQL generation and JDBC binding only: no Spring context, DataSource or connection. */
class PersistenceMappingTest {
    private static final Instant NOW = Instant.parse("2026-05-01T03:04:05Z");
    private static final OffsetDateTime UTC_NOW = NOW.atOffset(ZoneOffset.UTC);
    private static MybatisConfiguration configuration;

    @BeforeAll
    static void registerProductionMappings() {
        configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        // Mirrors application.yml's type-handlers-package without booting Spring.
        configuration.getTypeHandlerRegistry().register(UuidTypeHandler.class);
        configuration.getTypeHandlerRegistry().register(OffsetDateTimeTypeHandler.class);
        GlobalConfig global = GlobalConfigUtils.defaults();
        global.setMetaObjectHandler(new PersistenceConfig(Clock.fixed(NOW, ZoneId.of("Asia/Shanghai"))));
        // AUTO table IDs do not use this; avoid the default generator's host lookup.
        global.setIdentifierGenerator(entity -> 1L);
        GlobalConfigUtils.setGlobalConfig(configuration, global);
        configuration.addMapper(FeedingRepository.class);
        configuration.addMapper(TaskRepository.class);
        new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void feedingUpdateBindsNullableFieldsAsSqlNullAndNeverUpdatesCreatedAt() throws Exception {
        FeedingRecord feeding = new FeedingRecord();
        feeding.setId(11L);
        feeding.setCreatedAt(UTC_NOW.minusDays(2));
        assertNullUpdate(FeedingRepository.class, feeding,
                new String[]{"breastSide", "endTime", "amountMl", "note"},
                new String[]{"breast_side", "end_time", "amount_ml", "note"});
    }

    @Test
    void taskUpdateBindsNullableFieldsAsSqlNullAndNeverUpdatesCreatedAt() throws Exception {
        TodoTask task = new TodoTask();
        task.setId(12L);
        task.setCreatedAt(UTC_NOW.minusDays(2));
        assertNullUpdate(TaskRepository.class, task,
                new String[]{"remindTime", "completedAt", "description"},
                new String[]{"remind_time", "completed_at", "description"});
    }

    @Test
    void enumMappingsWriteNamesAndReadNamesRatherThanOrdinals() throws Exception {
        FeedingRecord feeding = new FeedingRecord();
        feeding.setId(11L);
        feeding.setFeedingType(FeedingType.BREAST_DIRECT);
        feeding.setBreastSide(BreastSide.LEFT);
        assertEnumMappings(FeedingRepository.class, feeding,
                new String[]{"feedingType", "breastSide"},
                new Enum<?>[]{FeedingType.BREAST_DIRECT, BreastSide.LEFT});

        TodoTask task = new TodoTask();
        task.setId(12L);
        task.setStatus(TaskStatus.DONE);
        task.setRecurrenceType(TaskRecurrenceType.WEEKLY);
        assertEnumMappings(TaskRepository.class, task,
                new String[]{"status", "recurrenceType"},
                new Enum<?>[]{TaskStatus.DONE, TaskRecurrenceType.WEEKLY});
    }

    @Test
    void uuidMappingsBindNativeUuidAndReadUuidIncludingNull() throws Exception {
        UUID requestId = UUID.fromString("7c125959-dcdf-424b-9334-11d1b5cd0001");
        FeedingRecord feeding = new FeedingRecord();
        feeding.setId(11L);
        feeding.setClientRequestId(requestId);
        TodoTask task = new TodoTask();
        task.setId(12L);
        task.setClientRequestId(requestId);
        for (Class<?> mapper : Arrays.asList(FeedingRepository.class, TaskRepository.class)) {
            Object entity = mapper == FeedingRepository.class ? feeding : task;
            MappedStatement update = statement(mapper, "updateById");
            Map<String, Object> parameters = entityParameters(entity);
            BoundSql sql = update.getBoundSql(parameters);
            // Map-based updateById uses UnknownTypeHandler, which resolves the UUID at bind time.
            assertThat(configuration.getTypeHandlerRegistry().getTypeHandler(UUID.class))
                    .isInstanceOf(UuidTypeHandler.class);
            PreparedStatement jdbc = bind(update, parameters, sql);
            verify(jdbc).setObject(index(sql, "et.clientRequestId"), requestId);

            TypeHandler<?> handler = resultMapping(mapper, "clientRequestId").getTypeHandler();
            assertThat(handler).isInstanceOf(UuidTypeHandler.class);
            ResultSet result = mock(ResultSet.class);
            when(result.getObject("client_request_id", UUID.class)).thenReturn(requestId);
            when(result.getObject(1, UUID.class)).thenReturn(requestId);
            assertThat(handler.getResult(result, "client_request_id")).isEqualTo(requestId);
            assertThat(handler.getResult(result, 1)).isEqualTo(requestId);
            assertThat(handler.getResult(result, "missing")).isNull();
            assertThat(handler.getResult(result, 2)).isNull();
            CallableStatement call = mock(CallableStatement.class);
            when(call.getObject(1)).thenReturn(requestId);
            assertThat(handler.getResult(call, 1)).isEqualTo(requestId);
            assertThat(handler.getResult(call, 2)).isNull();
        }
    }

    @Test
    void dateTimeMappingPreservesInstantAndReadsInSystemTimezone() throws Exception {
        FeedingRecord feeding = new FeedingRecord();
        feeding.setId(11L);
        feeding.setStartTime(UTC_NOW);
        MappedStatement update = statement(FeedingRepository.class, "updateById");
        Map<String, Object> parameters = entityParameters(feeding);
        BoundSql sql = update.getBoundSql(parameters);
        PreparedStatement jdbc = bind(update, parameters, sql);
        verify(jdbc).setObject(index(sql, "et.startTime"), UTC_NOW);

        TypeHandler<?> handler = resultMapping(FeedingRepository.class, "startTime").getTypeHandler();
        assertThat(handler).isInstanceOf(OffsetDateTimeTypeHandler.class);
        ResultSet result = mock(ResultSet.class);
        when(result.getObject("start_time", OffsetDateTime.class)).thenReturn(UTC_NOW);
        OffsetDateTime value = (OffsetDateTime) handler.getResult(result, "start_time");
        assertThat(value.toInstant()).isEqualTo(NOW);
        assertThat(value.getOffset()).isEqualTo(ZoneId.systemDefault().getRules().getOffset(NOW));
    }

    @Test
    void paginationCountDropsOrderingButKeepsPredicatesAndListKeepsOrderingAndOffset() {
        // Exercise the real Mapper proxy/default page method; intercept only SqlSession execution.
        SqlSession session = mock(SqlSession.class);
        when(session.getConfiguration()).thenReturn(configuration);
        List<BoundSql> generated = new ArrayList<BoundSql>();
        when(session.<Long>selectOne(anyString(), any())).thenAnswer(invocation -> {
            generated.add(configuration.getMappedStatement(invocation.getArgument(0))
                    .getBoundSql(invocation.getArgument(1)));
            return 41L;
        });
        FeedingRecord record = new FeedingRecord();
        when(session.<FeedingRecord>selectList(anyString(), any())).thenAnswer(invocation -> {
            generated.add(configuration.getMappedStatement(invocation.getArgument(0))
                    .getBoundSql(invocation.getArgument(1)));
            return Collections.singletonList(record);
        });
        FeedingRepository repository = configuration.getMapper(FeedingRepository.class, session);
        OffsetDateTime from = UTC_NOW.minusDays(1);
        RecordPage<FeedingRecord> page = repository
                .findAllByBabyIdAndDeletedAtIsNullAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeDescIdDesc(
                        7L, from, UTC_NOW, PageSpec.of(2, 10));

        assertThat(generated).hasSize(2);
        String count = normalized(generated.get(0));
        String list = normalized(generated.get(1));
        assertThat(count).contains("SELECT COUNT(", "baby_id = ?", "deleted_at IS NULL",
                "start_time >= ?", "start_time < ?").doesNotContain("ORDER BY", "LIMIT", "OFFSET");
        assertThat(list).contains("baby_id = ?", "deleted_at IS NULL", "start_time >= ?", "start_time < ?",
                "ORDER BY start_time DESC,id DESC", "LIMIT 10 OFFSET 20");
        assertThat(parameterValues(generated.get(0))).containsExactly(7L, from, UTC_NOW);
        assertThat(parameterValues(generated.get(1))).containsExactly(7L, from, UTC_NOW);
        assertThat(page.getContent()).containsExactly(record);
        assertThat(page.getTotalElements()).isEqualTo(41L);
        assertThat(page.getNumber()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(10);
    }

    @Test
    void taskLockAndIdempotenceQueriesKeepTheirDifferentSoftDeleteSemantics() {
        SqlSession session = mock(SqlSession.class);
        when(session.getConfiguration()).thenReturn(configuration);
        List<BoundSql> generated = new ArrayList<BoundSql>();
        when(session.<TodoTask>selectList(anyString(), any())).thenAnswer(invocation -> {
            generated.add(configuration.getMappedStatement(invocation.getArgument(0))
                    .getBoundSql(invocation.getArgument(1)));
            return Collections.emptyList();
        });
        TaskRepository repository = configuration.getMapper(TaskRepository.class, session);
        repository.findByIdAndDeletedAtIsNullForUpdate(12L);
        UUID requestId = UUID.fromString("7c125959-dcdf-424b-9334-11d1b5cd0001");
        repository.findByClientRequestId(requestId);
        assertThat(generated).hasSize(2);
        assertThat(normalized(generated.get(0))).contains("id = ?", "deleted_at IS NULL", "FOR UPDATE");
        assertThat(normalized(generated.get(1))).contains("client_request_id = ?")
                .doesNotContain("deleted_at IS NULL");
        assertThat(parameterValues(generated.get(1))).containsExactly(requestId);
    }

    @Test
    void parameterHandlerFillsUtcAuditTimesAndOverwritesOldUpdatedAtOnly() {
        for (Class<?> mapper : Arrays.asList(FeedingRepository.class, TaskRepository.class)) {
            AuditedEntity entity = mapper == FeedingRepository.class ? new FeedingRecord() : new TodoTask();
            MappedStatement insert = statement(mapper, "insert");
            configuration.newParameterHandler(insert, entity, insert.getBoundSql(entity));
            assertThat(entity.getCreatedAt()).isEqualTo(UTC_NOW);
            assertThat(entity.getUpdatedAt()).isEqualTo(UTC_NOW);

            OffsetDateTime created = UTC_NOW.minusDays(3);
            entity.setCreatedAt(created);
            entity.setUpdatedAt(UTC_NOW.minusDays(1));
            MappedStatement update = statement(mapper, "updateById");
            Map<String, Object> parameters = entityParameters(entity);
            BoundSql sql = update.getBoundSql(parameters);
            configuration.newParameterHandler(update, parameters, sql);
            assertThat(entity.getCreatedAt()).isEqualTo(created);
            assertThat(entity.getUpdatedAt()).isEqualTo(UTC_NOW);
            assertThat(entity.getUpdatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
            assertThat(normalized(sql)).contains("updated_at=?").doesNotContain("created_at");
        }
    }

    private static void assertNullUpdate(Class<?> mapper, AuditedEntity entity,
                                         String[] properties, String[] columns) throws Exception {
        MappedStatement update = statement(mapper, "updateById");
        Map<String, Object> parameters = entityParameters(entity);
        BoundSql sql = update.getBoundSql(parameters);
        assertThat(normalized(sql)).contains("WHERE id=?", "updated_at=?").doesNotContain("created_at");
        assertThat(sql.getParameterMappings()).extracting(ParameterMapping::getProperty)
                .doesNotContain("et.createdAt");
        PreparedStatement jdbc = bind(update, parameters, sql);
        for (int i = 0; i < properties.length; i++) {
            String property = "et." + properties[i];
            assertThat(normalized(sql)).contains(columns[i] + "=?");
            assertThat(configuration.newMetaObject(parameters).getValue(property)).isNull();
            ParameterMapping mapping = mapping(sql, property);
            JdbcType jdbcType = mapping.getJdbcType() == null ? configuration.getJdbcTypeForNull() : mapping.getJdbcType();
            verify(jdbc).setNull(index(sql, property), jdbcType.TYPE_CODE);
        }
    }

    private static void assertEnumMappings(Class<?> mapper, Object entity,
                                           String[] properties, Enum<?>[] values) throws Exception {
        MappedStatement update = statement(mapper, "updateById");
        Map<String, Object> parameters = entityParameters(entity);
        BoundSql sql = update.getBoundSql(parameters);
        PreparedStatement jdbc = bind(update, parameters, sql);
        for (int i = 0; i < properties.length; i++) {
            verify(jdbc).setString(index(sql, "et." + properties[i]), values[i].name());
            ResultMapping mapping = resultMapping(mapper, properties[i]);
            ResultSet result = mock(ResultSet.class);
            when(result.getString(mapping.getColumn())).thenReturn(values[i].name());
            assertThat(mapping.getTypeHandler().getResult(result, mapping.getColumn())).isEqualTo(values[i]);
        }
    }

    private static MappedStatement statement(Class<?> mapper, String method) {
        return configuration.getMappedStatement(mapper.getName() + "." + method);
    }

    private static Map<String, Object> entityParameters(Object entity) {
        return Collections.singletonMap("et", entity);
    }

    private static PreparedStatement bind(MappedStatement statement, Object parameters, BoundSql sql) throws java.sql.SQLException {
        PreparedStatement jdbc = mock(PreparedStatement.class);
        configuration.newParameterHandler(statement, parameters, sql).setParameters(jdbc);
        return jdbc;
    }

    private static ParameterMapping mapping(BoundSql sql, String property) {
        return sql.getParameterMappings().stream().filter(item -> property.equals(item.getProperty()))
                .findFirst().orElseThrow(() -> new AssertionError("Missing SQL parameter: " + property));
    }

    private static int index(BoundSql sql, String property) {
        return sql.getParameterMappings().indexOf(mapping(sql, property)) + 1;
    }

    private static ResultMapping resultMapping(Class<?> mapper, String property) {
        return statement(mapper, "selectById").getResultMaps().get(0).getResultMappings().stream()
                .filter(item -> property.equals(item.getProperty())).findFirst()
                .orElseThrow(() -> new AssertionError("Missing result mapping: " + property));
    }

    private static String normalized(BoundSql sql) {
        return sql.getSql().replaceAll("\\s+", " ").trim();
    }

    private static List<Object> parameterValues(BoundSql sql) {
        List<Object> values = new ArrayList<Object>();
        for (ParameterMapping mapping : sql.getParameterMappings()) {
            String property = mapping.getProperty();
            values.add(sql.hasAdditionalParameter(property) ? sql.getAdditionalParameter(property)
                    : configuration.newMetaObject(sql.getParameterObject()).getValue(property));
        }
        return values;
    }
}
