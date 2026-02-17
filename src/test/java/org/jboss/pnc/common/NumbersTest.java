/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014-2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class NumbersTest {

    @Test
    public void longToBytesAndBack() {
        long l = 1234567890123456789L;
        byte[] bytes = Numbers.longToBytes(l);
        long longFromBytes = Numbers.bytesToLong(bytes);
        Assertions.assertEquals(l, longFromBytes);
    }

    @Test
    public void convertDecimalToBase32AndBack() {
        long l = 1234567890123456789L;
        convertDecimalToBase32AndBack(1234567890123456789L);
        convertDecimalToBase32AndBack(4242L);
        convertDecimalToBase32AndBack(Long.MAX_VALUE);
        convertDecimalToBase32AndBack(Long.MIN_VALUE);
        convertDecimalToBase32AndBack(0L);
        convertDecimalToBase32AndBack(0xffffffffffffffffL);
        convertDecimalToBase32AndBack(-2975281302211218003L);
    }

    public void convertDecimalToBase32AndBack(long decimal) {
        String base32 = Numbers.decimalToBase32(decimal);
        System.out.println(base32);
        long backToDecimal = Numbers.base32ToDecimal(base32);
        Assertions.assertEquals(decimal, backToDecimal);
    }

    @Test
    public void failToConvertBase32Nonsense() {
        Numbers.base32ToDecimal("AAAAAAAAAAAAA");
        try {
            Numbers.base32ToDecimal("AAAAAAAAAAAA");
            fail("Should fail when wrong number of digits is used");
        } catch (IllegalArgumentException ex) {
        }
        try {
            Numbers.base32ToDecimal("AAAAAAAAAAAAAA");
            fail("Should fail when wrong number of digits is used");
        } catch (IllegalArgumentException ex) {
        }
        try {
            Numbers.base32ToDecimal("This is Nonsense01#");
            fail("Should fail when nonsense used");
        } catch (IllegalArgumentException ex) {
        }
        try {
            Long aaa = Numbers.base32ToDecimal("aAAAAAAAAAAAA");
            fail("Should fail when wrong digits are used " + aaa);
        } catch (IllegalArgumentException ex) {
        }
    }

    @Test
    public void convertDecimalToBase64() {
        long decimal = 4242L;
        String base64 = Numbers.decimalToBase64(decimal);
        System.out.println(base64);
        long backToDecimal = Numbers.base64ToDecimal(base64);

        Assertions.assertEquals(decimal, backToDecimal);
    }

    @Test
    public void base64ToDecimal() {
        String base64 = "100002";
        long backToDecimal = Numbers.base64ToDecimal(base64);

        Assertions.assertEquals(3612161235L, backToDecimal);
    }
}
