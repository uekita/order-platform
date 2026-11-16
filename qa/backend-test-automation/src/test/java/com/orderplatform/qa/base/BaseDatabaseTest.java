package com.orderplatform.qa.base;

import com.orderplatform.qa.db.OrderDbRepository;
import com.orderplatform.qa.test.TestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public abstract class BaseDatabaseTest extends BaseApiTest {

    protected OrderDbRepository db;

    @BeforeAll
    static void requireDatabase() {
        TestContext.database().assertReachable();
    }

    @BeforeEach
    void createDatabaseRepository() {
        db = TestContext.orderDbRepository();
    }
}
