package com.kaffe.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RuntimeDatabaseRoleGuardTest {
    @Test
    void rejectsPrivilegesAndUnknownResults() {
        for (Boolean result : new Boolean[]{true, null}) {
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(result);
            assertThatThrownBy(() -> new RuntimeDatabaseRoleGuard(jdbc).verify())
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void acceptsOnlyAnExplicitSafeResult() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);
        assertThatCode(() -> new RuntimeDatabaseRoleGuard(jdbc).verify()).doesNotThrowAnyException();
    }

    @Test
    void refusesToStartIfMetadataCannotBeChecked() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class)))
                .thenThrow(new DataAccessResourceFailureException("fixture unavailable"));
        assertThatThrownBy(() -> new RuntimeDatabaseRoleGuard(jdbc).verify())
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
}
