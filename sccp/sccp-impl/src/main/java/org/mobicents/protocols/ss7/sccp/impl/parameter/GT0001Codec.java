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

package org.mobicents.protocols.ss7.sccp.impl.parameter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.mobicents.protocols.ss7.indicator.NatureOfAddress;
import org.mobicents.protocols.ss7.sccp.parameter.GT0001;
import org.mobicents.protocols.ss7.sccp.parameter.GlobalTitle;
import org.mobicents.protocols.ss7.utils.Utils;

/**
 *
 * @author kulikov
 */
public class GT0001Codec extends GTCodec {

    private GT0001 gt;
    
    protected GT0001Codec() {
    }
    
    public GT0001Codec(GT0001 gt) {
        this.gt = gt;
    }
    
    
    public GlobalTitle decode(InputStream in) throws IOException {
        int b = in.read() & 0xff;
        
        NatureOfAddress nai = NatureOfAddress.valueOf(b & 0x7f);
        boolean odd = (b & 0x80) == 0x80;

        return new GT0001(nai, Utils.toBCD(in, odd));
    }

    
    public void encode(OutputStream out) throws IOException {        
        // determine if number of digits is even or odd
        String digits = gt.getDigits();
        boolean odd = (digits.length() % 2) != 0;
        
        // encoding first byte
        int b = 0x00;
        if (odd) {
            b = b | (byte) 0x80;
        }
        
        // adding nature of address indicator
        b = b | (byte) gt.getNoA().getValue();
        
        //write first byte
        out.write((byte) b);
        out.write(Utils.parseTBCD(digits));
    }

}
