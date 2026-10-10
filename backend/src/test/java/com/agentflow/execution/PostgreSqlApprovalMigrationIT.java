package com.agentflow.execution;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class PostgreSqlApprovalMigrationIT {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
    @Test void preservesPostgreSqlRunStepAndAuditHistoryDuringUpgrade() throws Exception {
        verifyUpgrade(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
    }
    @Test void preservesH2RunStepAndAuditHistoryDuringUpgrade() throws Exception {
        verifyUpgrade("jdbc:h2:mem:approval-upgrade;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
    }
    private void verifyUpgrade(String url,String username,String password) throws Exception {
        Flyway.configure().dataSource(url,username,password).target("4").load().migrate();
        try(var db=DriverManager.getConnection(url,username,password);var sql=db.createStatement()) {
            sql.executeUpdate("INSERT INTO users VALUES ('00000000-0000-0000-0000-000000000001','old-owner','unused','USER',CURRENT_TIMESTAMP)");
            sql.executeUpdate("INSERT INTO workflows (id,owner_id,name,description,current_version,created_at,updated_at) VALUES "
                    + "('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','Old workflow','History',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            sql.executeUpdate("INSERT INTO workflow_versions VALUES ('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000002',1,'Old workflow','History','{}',CURRENT_TIMESTAMP)");
            sql.executeUpdate("INSERT INTO workflow_runs(id,workflow_id,version_id,version_number,owner_id,idempotency_key,input,context,status,created_at) VALUES "
                    + "('00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003',1,'00000000-0000-0000-0000-000000000001','old-key','{}','{}','SUCCEEDED',CURRENT_TIMESTAMP)");
            sql.executeUpdate("INSERT INTO node_executions(id,run_id,node_id,ordinal,operation,status,attempts) VALUES "
                    + "('00000000-0000-0000-0000-000000000005','00000000-0000-0000-0000-000000000004','old-node',0,'RECEIVE_INPUT','SUCCEEDED',1)");
            sql.executeUpdate("INSERT INTO audit_events VALUES ('00000000-0000-0000-0000-000000000006','00000000-0000-0000-0000-000000000004',0,NULL,'RUN_SUCCEEDED',CURRENT_TIMESTAMP,NULL,NULL)");
        }
        Flyway.configure().dataSource(url,username,password).load().migrate();
        try(var db=DriverManager.getConnection(url,username,password);var sql=db.createStatement()) {
            try(var rows=sql.executeQuery("SELECT status,mode,paused_millis FROM workflow_runs")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("SUCCEEDED");
                assertThat(rows.getString(2)).isEqualTo("DETERMINISTIC"); assertThat(rows.getLong(3)).isZero();
            }
            try(var rows=sql.executeQuery("SELECT status,attempts FROM node_executions")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("SUCCEEDED"); assertThat(rows.getInt(2)).isEqualTo(1);
            }
            try(var rows=sql.executeQuery("SELECT COUNT(*) FROM audit_events")) {rows.next();assertThat(rows.getInt(1)).isEqualTo(1);}
        }
    }
}
