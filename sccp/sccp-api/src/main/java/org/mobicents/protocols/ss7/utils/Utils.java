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

package org.mobicents.protocols.ss7.utils;

import java.io.IOException;
import java.io.InputStream;

/**
 * This class contains various static utility methods.
 *
 * @author Oleg Kulikov
 *
 */
public class Utils {

    private static String cTBCDSymbolString = "0123456789abcdef";
    private static char[] cTBCDSymbols = cTBCDSymbolString.toCharArray();

    public static String toBCD(InputStream in, boolean isOdd) throws IOException {
        int b;

        StringBuilder sb = new StringBuilder();

        while (in.available() > 0) {
            b = in.read() & 0xff;
            sb.append(cTBCDSymbols[b & 0x0f]).append(cTBCDSymbols[(b & 0xf0) >> 4]);
        }

        if (isOdd) {
            sb.setLength(sb.length() - 1);
        }

        String digits = sb.toString();

        return digits;
    }

    public static byte[] parseTBCD(String tbcd) {
        int length = (tbcd == null ? 0 : tbcd.length());
        int size = (length + 1) / 2;
        byte[] buffer = new byte[size];

        for (int i = 0, i1 = 0, i2 = 1; i < size; ++i, i1 += 2, i2 += 2) {

            char c = tbcd.charAt(i1);
            int n2 = getTBCDNibble(c, i1);
            int octet = 0;
            int n1 = 0;
            if (i2 < length) {
                c = tbcd.charAt(i2);
                n1 = getTBCDNibble(c, i2);
            }
            octet = (n1 << 4) + n2;
            buffer[i] = (byte) (octet & 0xFF);
        }

        return buffer;
    }

    private static int getTBCDNibble(char c, int i1) {

        int n = Character.digit(c, 10);

        if (n < 0 || n > 9) {
            switch (c) {
                case 'a':
                    n = 10;
                    break;
                case 'b':
                    n = 11;
                    break;
                case 'c':
                    n = 12;
                    break;
                case 'd':
                    n = 13;
                    break;
                case 'e':
                    n = 14;
                    break;
                case 'f':
                    n = 15;
                    break;
                default:
                    throw new NumberFormatException("Bad character '" + c
                            + "' at position " + i1);
            }
        }
        return n;
    }

    /* Hex chars */
    private static final byte[] HEX_CHAR = new byte[]
            {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    /*
     * Helper function that dumps an array of bytes in the hexadecimal format.
     */
    public static final String hexDump(byte[] buffer) {
        if (buffer == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < buffer.length; i++) {
            sb.append("0x").append((char) (HEX_CHAR[(buffer[i] & 0x00F0) >> 4])).append(
                    (char) (HEX_CHAR[buffer[i] & 0x000F])).append(' ');
        }

        return sb.toString();
    }

}
