package com.example.kintai.shared.domain.exception;

/**
 * リソース未検出例外
 *
 * <p>リポジトリの検索で対象が見つからない場合にスローする。
 * GlobalExceptionHandler で 404 Not Found にマッピングされる。</p>
 */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
