package org.bike.api;

import java.io.IOException;

public final class ApiException extends IOException {
    private final int status;
    private final String code;
    private final String searchQuery;
    public ApiException(int status, String code, String message) { this(status, code, message, null); }
    public ApiException(int status, String code, String message, String searchQuery) {
        super(message);
        this.status = status;
        this.code = code;
        this.searchQuery = searchQuery;
    }
    public int status() { return status; }
    public String code() { return code; }
    public String searchQuery() { return searchQuery; }
}
