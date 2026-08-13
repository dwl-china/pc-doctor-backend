package com.pcdoctor.security;

import com.pcdoctor.common.ErrorCode;
import com.pcdoctor.common.Result;
import com.pcdoctor.entity.User;
import com.pcdoctor.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * 无状态 JWT 过滤器：解析 Authorization: Bearer &lt;token&gt;，装配 SecurityContext。
 * 角色从数据库实时读取（角色变更即时生效）；强制改密用户仅放行白名单接口。
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 强制改密期间仍可访问的接口（登录/注册为公开接口，须放行避免死锁） */
    private static final Set<String> MUST_CHANGE_WHITELIST = Set.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/me", "/api/users/me/password");

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserMapper userMapper, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtil.parse(header.substring(7));
                User user = userMapper.selectById(claims.getSubject());
                if (user == null) {
                    log.warn("token 对应用户不存在: {}", claims.getSubject());
                } else if (isMustChange(user) && !MUST_CHANGE_WHITELIST.contains(request.getRequestURI())) {
                    writeJson(response, HttpServletResponse.SC_FORBIDDEN, Result.fail(ErrorCode.PASSWORD_MUST_CHANGE));
                    return;
                } else {
                    var authorities = List.of(new SimpleGrantedAuthority(roleOf(user.getLevel())));
                    var authentication = new UsernamePasswordAuthenticationToken(user.getUserId(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException e) {
                // 无效/过期 token 静默放行，受保护接口由 entry point 统一返回 401
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    /** 角色映射：USER=0 / DOCTOR=1 / ADMIN=2（分析报告 #6） */
    static String roleOf(Integer level) {
        if (level == null) {
            return "ROLE_USER";
        }
        return switch (level) {
            case 1 -> "ROLE_DOCTOR";
            case 2 -> "ROLE_ADMIN";
            default -> "ROLE_USER";
        };
    }

    private boolean isMustChange(User user) {
        return user.getMustChangePassword() != null && user.getMustChangePassword() == 1;
    }

    private void writeJson(HttpServletResponse response, int status, Result<?> body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}