package dev.oumuv.budlog.setting;

import dev.oumuv.budlog.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingService {

    private static final short SETTING_ID = 1;

    private final SettingRepository repository;

    public SettingService(SettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AppSetting requireSetting() {
        return repository.findById(SETTING_ID)
                .orElseThrow(() -> new IllegalStateException("Default app_setting row is missing"));
    }

    @Transactional(readOnly = true)
    public SettingResponse get() {
        return toResponse(requireSetting());
    }

    @Transactional
    public SettingResponse update(SettingRequest request) {
        if (request.getDefaultFeedingIntervalMin() % 5 != 0) {
            throw BusinessException.validation("默认喂奶间隔必须是 5 分钟的倍数");
        }
        AppSetting setting = requireSetting();
        setting.setDefaultFeedingIntervalMin(request.getDefaultFeedingIntervalMin());
        setting.setReminderSoundEnabled(request.getReminderSoundEnabled());
        setting.setReminderVibrateEnabled(request.getReminderVibrateEnabled());
        return toResponse(repository.save(setting));
    }

    public SettingResponse toResponse(AppSetting setting) {
        return new SettingResponse(
                setting.getDefaultFeedingIntervalMin(),
                setting.getFeedingIntervalAnchor(),
                setting.getReminderSoundEnabled(),
                setting.getReminderVibrateEnabled()
        );
    }
}

