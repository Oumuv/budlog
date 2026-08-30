package dev.oumuv.budlog.setting;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<AppSetting, Short> {
}

