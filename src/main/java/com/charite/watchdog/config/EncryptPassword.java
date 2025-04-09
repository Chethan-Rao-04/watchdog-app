package com.charite.watchdog.config;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;



public class EncryptPassword {
    public static void main(String[] args) {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setPassword("watchdog123"); // Same key as above
        encryptor.setAlgorithm("PBEWithMD5AndDES");
        String encrypted = encryptor.encrypt("flwdkegrmflrdrmg");
        System.out.println("Encrypted: ENC(" + encrypted + ")");

        // Optional: Verify decryption
        String decrypted = encryptor.decrypt(encrypted);
        System.out.println("Decrypted: " + decrypted);
    }
    }
