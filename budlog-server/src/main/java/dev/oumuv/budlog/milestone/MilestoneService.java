package dev.oumuv.budlog.milestone;

import dev.oumuv.budlog.baby.BabyProfile;
import dev.oumuv.budlog.baby.BabyService;
import dev.oumuv.budlog.common.BusinessException;
import dev.oumuv.budlog.common.TimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class MilestoneService {

    private final MilestoneRepository repository;
    private final BabyService babyService;
    private final TimeService timeService;

    public MilestoneService(MilestoneRepository repository, BabyService babyService, TimeService timeService) {
        this.repository = repository;
        this.babyService = babyService;
        this.timeService = timeService;
    }

    @Transactional(readOnly = true)
    public List<MilestoneResponse> list() {
        BabyProfile baby = babyService.requireCurrent();
        List<MilestoneResponse> result = new ArrayList<MilestoneResponse>(systemMilestones(baby));
        for (CustomMilestone milestone : repository.findAllByBabyIdAndDeletedAtIsNullOrderByTargetTimeAsc(baby.getId())) {
            result.add(toResponse(milestone));
        }
        result.sort(Comparator.comparing(MilestoneResponse::getTargetTime));
        return result;
    }

    @Transactional
    public MilestoneResponse create(MilestoneRequest request) {
        CustomMilestone duplicate = repository.findByClientRequestId(request.getClientRequestId()).orElse(null);
        if (duplicate != null) {
            return toResponse(duplicate);
        }

        CustomMilestone milestone = new CustomMilestone();
        milestone.setBaby(babyService.requireCurrent());
        milestone.setClientRequestId(request.getClientRequestId());
        apply(milestone, request);
        return toResponse(repository.save(milestone));
    }

    @Transactional
    public MilestoneResponse update(Long id, MilestoneRequest request) {
        CustomMilestone milestone = require(id);
        apply(milestone, request);
        return toResponse(repository.save(milestone));
    }

    @Transactional
    public void delete(Long id) {
        CustomMilestone milestone = require(id);
        milestone.setDeletedAt(timeService.now());
        repository.save(milestone);
    }

    public List<MilestoneResponse> systemMilestones(BabyProfile baby) {
        ZoneId zone = timeService.zone(baby.getTimezone());
        ZonedDateTime birth = baby.getBirthTime().atZoneSameInstant(zone);
        List<MilestoneResponse> result = new ArrayList<MilestoneResponse>();
        result.add(system("FULL_MONTH", "满月", birth.plusMonths(1), zone));
        result.add(system("DAY_100", "百天", birth.plusDays(99), zone));
        result.add(system("HALF_YEAR", "半岁", birth.plusMonths(6), zone));
        result.add(system("ONE_YEAR", "周岁", birth.plusYears(1), zone));
        return result;
    }

    private MilestoneResponse system(String code, String title, ZonedDateTime target, ZoneId zone) {
        return MilestoneResponse.builder()
                .code(code)
                .system(true)
                .title(title)
                .targetTime(target.toOffsetDateTime())
                .daysDifference(daysDifference(target.toOffsetDateTime(), zone))
                .build();
    }

    private MilestoneResponse toResponse(CustomMilestone milestone) {
        ZoneId zone = timeService.zone(milestone.getBaby().getTimezone());
        return MilestoneResponse.builder()
                .id(milestone.getId())
                .code("CUSTOM")
                .system(false)
                .title(milestone.getTitle())
                .targetTime(milestone.getTargetTime())
                .daysDifference(daysDifference(milestone.getTargetTime(), zone))
                .note(milestone.getNote())
                .build();
    }

    private long daysDifference(OffsetDateTime target, ZoneId zone) {
        LocalDate today = timeService.today(zone.getId());
        LocalDate targetDate = target.atZoneSameInstant(zone).toLocalDate();
        return ChronoUnit.DAYS.between(today, targetDate);
    }

    private void apply(CustomMilestone milestone, MilestoneRequest request) {
        milestone.setTitle(request.getTitle().trim());
        milestone.setTargetTime(request.getTargetTime());
        milestone.setNote(normalize(request.getNote()));
    }

    private CustomMilestone require(Long id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> BusinessException.notFound("纪念日不存在或已删除"));
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}

