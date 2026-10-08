package com.jmifx.demo;

import com.jmifx.demo.entity.City;
import io.jmix.core.UnconstrainedDataManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class CityPersistenceTest {

    @Autowired
    UnconstrainedDataManager dataManager;

    @Test
    void saveAndReloadCity() {
        City city = dataManager.create(City.class);
        city.setName("Berlin");
        City saved = dataManager.save(city);

        UUID id = saved.getId();
        assertNotNull(id);

        City loaded = dataManager.load(City.class).id(id).one();
        assertEquals("Berlin", loaded.getName());
        assertEquals(1, loaded.getVersion());
    }
}
