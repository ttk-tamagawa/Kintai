package com.example.kintai.shared.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT設定プロパティ — application.yml の app.jwt 配下をバインドする
 *
 * <p>secret: HS256署名に使用するシークレットキー（256bit以上必須）
 * expirationMs: トークンの有効期限（ミリ秒）</p>
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long expirationMs
) {
}
