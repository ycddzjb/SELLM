package com.sellm.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.sql.Connection;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SchemaSmokeTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void 能拿到H2数据源连接() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertThat(conn.isValid(2)).isTrue();
            assertThat(conn.getMetaData().getDatabaseProductName()).isEqualTo("H2");
        }
    }
}
