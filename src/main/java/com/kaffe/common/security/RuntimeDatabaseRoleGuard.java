package com.kaffe.common.security;

import org.springframework.jdbc.core.JdbcTemplate;

/** Read-only startup preflight. Activation is explicit until grants/RLS are validated per service. */
public final class RuntimeDatabaseRoleGuard {
    private static final String ROLE_CHECK = """
            select exists (
                select 1 from pg_roles r
                where pg_has_role(current_user, r.oid, 'MEMBER')
                  and (r.rolsuper or r.rolbypassrls or r.rolcreaterole or r.rolcreatedb or r.rolreplication)
            ) or exists (
                select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
                where n.nspname not like 'pg_%' and n.nspname <> 'information_schema'
                  and c.relkind in ('r', 'p', 'v', 'm', 'S', 'f')
                  and pg_has_role(current_user, c.relowner, 'MEMBER')
            ) or exists (
                select 1 from pg_namespace n
                where n.nspname not like 'pg_%' and n.nspname <> 'information_schema'
                  and has_schema_privilege(current_user, n.oid, 'CREATE')
            ) as unsafe_runtime_role
            """;

    private final JdbcTemplate jdbc;

    public RuntimeDatabaseRoleGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void verify() {
        // Query failures also prevent startup: inability to check is not approval.
        Boolean unsafe = jdbc.queryForObject(ROLE_CHECK, Boolean.class);
        if (!Boolean.FALSE.equals(unsafe)) {
            throw new IllegalStateException("Runtime database role is privileged, owns relations, or can create objects; use a dedicated non-owner execution role");
        }
    }
}
