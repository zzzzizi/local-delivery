package com.localdelivery;

import com.localdelivery.controller.ReadinessController;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReadinessTest {
    @Test void databaseAvailable() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        assertThat(new ReadinessController(jdbc).ready().getStatusCode().value()).isEqualTo(200);
    }
    @Test void databaseFailureIsUnavailableWithoutLeakingDetails() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenThrow(new DataAccessResourceFailureException("private database credentials"));
        var result = new ReadinessController(jdbc).ready();
        assertThat(result.getStatusCode().value()).isEqualTo(503);
        assertThat(result.getBody()).containsEntry("status", "unavailable").doesNotContainValue("private database credentials");
    }
}
