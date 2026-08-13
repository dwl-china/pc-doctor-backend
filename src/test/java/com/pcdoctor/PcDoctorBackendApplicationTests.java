package com.pcdoctor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 上下文冒烟测试：走 test profile（CI 无开发环境变量，dev profile 无法起上下文） */
@SpringBootTest
@ActiveProfiles("test")
class PcDoctorBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
