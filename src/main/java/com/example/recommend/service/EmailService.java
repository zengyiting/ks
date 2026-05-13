package com.example.recommend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件发送服务
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String mailPassword;
    private final boolean enableRealEmail;

    public EmailService(JavaMailSender mailSender,
                       @Value("${spring.mail.username:}") String fromEmail,
                       @Value("${spring.mail.password:}") String mailPassword) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.mailPassword = mailPassword;
        // 如果配置了邮箱账号和密码，则启用真实邮件发送
        this.enableRealEmail = fromEmail != null && !fromEmail.isBlank() && mailPassword != null && !mailPassword.isBlank();

        if (enableRealEmail) {
            logger.info("已配置真实邮件发送，发件人: {}", fromEmail);
        } else {
            logger.info("未配置邮件账号，将使用模拟发送模式");
        }
    }

    /**
     * 发送验证码邮件
     * @param toEmail 收件人邮箱
     * @param code 验证码
     */
    public void sendVerificationCode(String toEmail, String code) {
        String subject = "【推荐系统】验证码";
        String text = String.format("您的验证码是：%s\n\n验证码有效期5分钟，请及时使用。", code);

        if (enableRealEmail && fromEmail != null && !fromEmail.isBlank()) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromEmail);
                message.setTo(toEmail);
                message.setSubject(subject);
                message.setText(text);
                mailSender.send(message);
                logger.info("真实邮件发送成功，收件人: {}, 验证码: {}", toEmail, code);
            } catch (MailException e) {
                logger.error("真实邮件发送失败，收件人: {}, 错误: {}", toEmail, e.getMessage());
                // 如果真实发送失败，退回到模拟发送
                simulateSend(toEmail, code);
            }
        } else {
            simulateSend(toEmail, code);
        }
    }

    /**
     * 模拟发送邮件（打印到日志）
     */
    private void simulateSend(String toEmail, String code) {
        logger.info("【模拟发送邮箱验证码】邮箱: {}, 验证码: {}", toEmail, code);
        System.out.printf("【模拟发送邮箱验证码】邮箱: %s, 验证码: %s%n", toEmail, code);
    }

    /**
     * 是否启用真实邮件发送
     */
    public boolean isEnableRealEmail() {
        return enableRealEmail;
    }
}
