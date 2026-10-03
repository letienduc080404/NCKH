package com.duc.iot.iot_web_app.security;

import com.duc.iot.iot_web_app.model.User;
import com.duc.iot.iot_web_app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() == 0) {
            log.info("Không tìm thấy người dùng nào trong cơ sở dữ liệu. Đang tạo tài khoản mặc định...");
            
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin"));
            admin.setRole("ROLE_ADMIN");
            admin.setCreatedAt(LocalDateTime.now());
            
            userRepository.save(admin);
            
            log.info("Tài khoản mặc định đã được tạo: username='admin', password='admin'");
        }
    }
}
