package io.github.danielnguyen327.ledgersync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.support.CronExpression;

/** Reads the real schedule from application.yaml (tests switch it off) and checks when it fires. */
class SyncScheduleTests {

    @Test
    void banksSyncEveryThreeHoursAt17MinutesPast() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        CronExpression schedule = CronExpression.parse(yaml.getObject().getProperty("ledgersync.sync.cron"));

        LocalDateTime nine = LocalDateTime.of(2026, 10, 18, 9, 0);

        assertThat(schedule.next(nine)).isEqualTo(nine.withMinute(17));
        assertThat(schedule.next(nine.withMinute(17))).isEqualTo(nine.withHour(12).withMinute(17));
        assertThat(schedule.next(LocalDateTime.of(2026, 10, 18, 22, 0))).isEqualTo(LocalDateTime.of(2026, 10, 19, 0, 17));
    }
}
