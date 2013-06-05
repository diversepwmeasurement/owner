/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.reload;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Reloadable;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class ReloadTest {
    private static final String spec = "file:target/test-resources/ReloadableConfig.properties";
    private static File target;

    @BeforeClass
    public static void beforeClass() throws MalformedURLException {
        target = new File(new URL(spec).getFile());
    }

    @Sources(spec)
    public interface ReloadableConfig extends Config, Reloadable {
        Integer minimumAge();
    }

    @Test
    public void testReload() throws Throwable {
        save(new Properties() {{
            setProperty("minimumAge", "18");
        }});

        ReloadableConfig cfg = ConfigFactory.create(ReloadableConfig.class);

        assertEquals(Integer.valueOf(18), cfg.minimumAge());

        save(new Properties() {{
            setProperty("minimumAge", "21");
        }});

        cfg.reload();
        assertEquals(Integer.valueOf(21), cfg.minimumAge());
    }

    @Test
    public void testReloadWithImportedProperties() throws Throwable {
        Properties props = new Properties() {{
           setProperty("minimumAge", "18");
        }};

        ReloadableConfig cfg = ConfigFactory.create(ReloadableConfig.class, props);
        assertEquals(Integer.valueOf(18), cfg.minimumAge());

        props.setProperty("minimumAge", "21"); // changing props doesn't reflect to cfg immediately
        assertEquals(Integer.valueOf(18), cfg.minimumAge());

        cfg.reload(); // the config gets reloaded, so the change in props gets reflected
        assertEquals(Integer.valueOf(21), cfg.minimumAge());
    }

    private void save(Properties p) throws Throwable {
        target.getParentFile().mkdirs();
        p.store(new FileWriter(target), "reloadable config example");
    }

    @After
    public void after() throws Throwable {
        target.delete();
    }

}