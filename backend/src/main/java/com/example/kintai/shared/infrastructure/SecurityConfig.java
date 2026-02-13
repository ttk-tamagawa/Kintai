package com.example.kintai.shared.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * セキュリティ設定（開発用の仮設定）
 *
 * タスク5-1でJWT認証を実装する際に本格的な設定に置き換える。
 * 現時点では全リクエストを許可し、開発をスムーズに進められるようにする。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(CorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
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
            // 全リクエストを許可（開発用。タスク5-1で認証設定に変更）
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
