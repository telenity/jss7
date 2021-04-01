/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.protocols.ss7.indicator;

import org.mobicents.protocols.ss7.utils.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The Encoding Scheme (ES) tells the receiving node how to translate the digits
 * from binary code
 * 
 * @author kulikov
 */
public enum EncodingScheme {
    UNKNOWN(0), BCD_ODD(1), BCD_EVEN(2);

    private int value;

    private EncodingScheme(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static EncodingScheme valueOf(int v) {
        switch (v) {
        case 0:
            return UNKNOWN;
        case 1:
            return BCD_ODD;
        case 2:
            return BCD_EVEN;
        default:
            return null;
        }
    }

    public String decodeDigits(InputStream in) throws IOException {
        return Utils.toBCD(in, value == 1);
    }

    public void encodeDigits(String digits, OutputStream out) throws IOException {
        out.write(Utils.parseTBCD(digits));
    }
}
