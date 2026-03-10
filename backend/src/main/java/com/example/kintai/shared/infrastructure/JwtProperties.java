package com.example.kintai.shared.infrastructure;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT設定プロパティ — application.yml の app.jwt 配下をバインドする
 *
 * <p>secret: HS256署名に使用するシークレットキー（256bit以上必須）。
 * 環境変数 JWT_SECRET で必ず指定すること。未設定の場合は起動時にエラーとなる。
 * expirationMs: トークンの有効期限（ミリ秒）</p>
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank(message = "JWT シークレットキーが設定されていません。環境変数 JWT_SECRET を設定してください")
        String secret,
        long expirationMs,
        long refreshExpirationMs
) {
}
