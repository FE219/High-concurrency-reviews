package com.hmdp.config;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    @Around("@annotation(com.hmdp.annotation.AuditLog)")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        UserDTO user = UserHolder.getUser();
        String ip = getClientIp();
        String operation = pjp.getSignature().toShortString();
        Long userId = user != null ? user.getId() : null;

        log.info("AUDIT: userId={}, ip={}, operation={}, start", userId, ip, operation);
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            log.info("AUDIT: userId={}, ip={}, operation={}, success, costMs={}",
                    userId, ip, operation, System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("AUDIT: userId={}, ip={}, operation={}, failed, costMs={}",
                    userId, ip, operation, System.currentTimeMillis() - start);
            throw e;
        }
    }

    private String getClientIp() {
        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";
        return attrs.getRequest().getRemoteAddr();
    }
}
