package com.a05.aiinterview.auth.service;

import jakarta.mail.MessagingException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@FunctionalInterface
public interface MailConnectionTester {

    void testConnection(JavaMailSenderImpl sender) throws MessagingException;
}
