package com.charite.watchdog.config;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;

@Configuration
@EnableEncryptableProperties  // Enables Jasypt property decryption
public class JasyptConfig {
    @Bean(name = "jasyptStringEncryptor")
    public StringEncryptor stringEncryptor() {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        String password = System.getenv("JASYPT_ENCRYPTOR_PASSWORD");
        if (password == null) {
            throw new IllegalStateException("JASYPT_ENCRYPTOR_PASSWORD environment variable is not set");
        }
        encryptor.setPassword(password); // Use env variable
        encryptor.setAlgorithm("PBEWithMD5AndDES");
        return encryptor;
    }
}

