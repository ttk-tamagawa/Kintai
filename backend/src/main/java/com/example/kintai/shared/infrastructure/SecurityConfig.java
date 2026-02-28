package com.example.kintai.shared.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * セキュリティ設定 — JWT認証 + ロールベース認可
 *
 * <p>JWTフィルターを UsernamePasswordAuthenticationFilter の前に配置し、
 * Bearer トークンによるステートレス認証を実現する。
 * メソッドレベルの認可は @PreAuthorize で各コントローラーに設定する。</p>
 *
 * <p>URL認可:
 * <ul>
 *   <li>/api/v1/auth/** → 認証不要（ログインエンドポイント）</li>
 *   <li>/api/** → 認証必須</li>
 *   <li>その他 → 全許可</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    public SecurityConfig(
            CorsConfigurationSource corsConfigurationSource,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            JwtAccessDeniedHandler jwtAccessDeniedHandler) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CORS設定を適用
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            // CSRF無効化（REST APIではトークンベース認証を使うため不要）
            .csrf(csrf -> csrf.disable())
            // セッション不使用（JWT認証で管理するため）
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 認証エラー・認可エラーのハンドラーを設定する
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler))
            // URL別の認可ルールを設定する
            .authorizeHttpRequests(auth -> auth
                // 認証エンドポイントは全許可
                .requestMatchers("/api/v1/auth/**").permitAll()
                // その他のAPIは認証必須
                .requestMatchers("/api/**").authenticated()
                // API以外（静的リソース等）は全許可
                .anyRequest().permitAll())
            // JWTフィルターを認証フィルターの前に配置する
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
