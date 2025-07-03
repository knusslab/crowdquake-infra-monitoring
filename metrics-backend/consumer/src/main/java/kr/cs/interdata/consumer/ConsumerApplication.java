package kr.cs.interdata.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(
		exclude = {
				org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
				org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class
		}
)
public class ConsumerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConsumerApplication.class, args);
	}

}
