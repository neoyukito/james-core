/*
 * Copyright 2014 Ghent University, Bayer CropScience.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jamesframework.core.exceptions;

import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;

/**
 * Test incompatible delta validation exception.
 * 
 * @author <a href="mailto:herman.debeukelaer@ugent.be">Herman De Beukelaer</a>
 */
public class IncompatibleDeltaValidationExceptionTest {

    static private final String MSG = "This is all your fault!";
    static private final Exception CAUSE = new RuntimeException("I caused it ...");

    /**
     * Print message when starting tests.
     */
    @BeforeClass
    public static void setUpClass() {
        System.out.println("# Testing IncompatibleDeltaValidationException ...");
    }

    /**
     * Print message when tests are complete.
     */
    @AfterClass
    public static void tearDownClass() {
        System.out.println("# Done testing IncompatibleDeltaValidationException!");
    }
    
    @Test
    public void testConstructor1() {
        IncompatibleDeltaValidationException ex = new IncompatibleDeltaValidationException();
        assertNull(ex.getCause());
        assertNull(ex.getMessage());
    }
    
    @Test
    public void testConstructor2() {
        IncompatibleDeltaValidationException ex = new IncompatibleDeltaValidationException(MSG);
        assertNull(ex.getCause());
        assertEquals(MSG, ex.getMessage());
    }
    
    @Test
    public void testConstructor3() {
        IncompatibleDeltaValidationException ex = new IncompatibleDeltaValidationException(CAUSE);
        assertEquals(CAUSE, ex.getCause());
        assertEquals(CAUSE.toString(), ex.getMessage());
    }
    
    @Test
    public void testConstructor4() {
        IncompatibleDeltaValidationException ex = new IncompatibleDeltaValidationException(MSG, CAUSE);
        assertEquals(CAUSE, ex.getCause());
        assertEquals(MSG, ex.getMessage());
    }

}