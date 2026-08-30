package dev.oumuv.budlog.baby;

import dev.oumuv.budlog.common.BusinessException;
import dev.oumuv.budlog.common.TimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class BabyService {

    private final BabyRepository repository;
    private final TimeService timeService;

    public BabyService(BabyRepository repository, TimeService timeService) {
        this.repository = repository;
        this.timeService = timeService;
    }

    @Transactional(readOnly = true)
    public Optional<BabyProfile> findCurrent() {
        return repository.findFirstByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public BabyProfile requireCurrent() {
        return findCurrent().orElseThrow(() -> BusinessException.notFound("请先设置宝宝资料"));
    }

    @Transactional(readOnly = true)
    public BabyResponse getCurrent() {
        return toResponse(requireCurrent());
    }

    @Transactional
    public BabyResponse save(BabyRequest request) {
        ZoneId zone = timeService.zone(request.getTimezone());
        if (request.getBirthTime().isAfter(timeService.now().plusMinutes(1))) {
            throw BusinessException.validation("出生时间不能晚于当前时间");
        }

        BabyProfile baby = repository.findFirstByOrderByIdAsc().orElseGet(BabyProfile::new);
        baby.setName(request.getName().trim());
        baby.setBirthTime(request.getBirthTime().withSecond(0).withNano(0));
        baby.setTimezone(zone.getId());
        baby.setNote(normalize(request.getNote()));
        return toResponse(repository.save(baby));
    }

    public BabyResponse toResponse(BabyProfile baby) {
        ZoneId zone = timeService.zone(baby.getTimezone());
        ZonedDateTime birth = baby.getBirthTime().atZoneSameInstant(zone);
        ZonedDateTime now = timeService.now().atZoneSameInstant(zone);
        LocalDate birthDate = birth.toLocalDate();
        LocalDate today = now.toLocalDate();

        long ageDay = ChronoUnit.DAYS.between(birthDate, today) + 1;
        Duration duration = Duration.between(baby.getBirthTime(), timeService.now());
        long totalHours = Math.max(0, duration.toHours());
        Period period = Period.between(birthDate, today);
        int months = Math.max(0, period.getYears() * 12 + period.getMonths());

        return BabyResponse.builder()
                .id(baby.getId())
                .name(baby.getName())
                .birthTime(baby.getBirthTime())
                .timezone(baby.getTimezone())
                .note(baby.getNote())
                .ageDayNumber(Math.max(1, ageDay))
                .ageDurationDays(totalHours / 24)
                .ageDurationHours(totalHours % 24)
                .ageMonths(months)
                .ageRemainingDays(Math.max(0, period.getDays()))
                .build();
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}

