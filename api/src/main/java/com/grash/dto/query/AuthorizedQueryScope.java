package com.grash.dto.query;

/** Constructed only by QueryAccessPolicy after loading fresh database authorization. */
public record AuthorizedQueryScope(long userId, long companyId) {
    public String cacheScope() { return "full-wo-user-" + userId; }
}
