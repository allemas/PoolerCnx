package com.allemas.pool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ProxyConfigSpec {
    @Test
    public void configuraitonAuto(){
        PoolConfig pool = PoolConfig.auto();
        Assertions.assertNotNull(pool);
    }
}
