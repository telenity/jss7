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

package org.mobicents.protocols.ss7.mtp;

/**
 * 
 * @author kulikov
 * @author baranowb
 */
public final class Mtp3 {

    public final static int TIMEOUT_T1_SLTM = 120;
    public final static int TIMEOUT_T2_SLTM = 900;
    public final static int _SI_SERVICE_SCCP = 3;
    public final static int _SI_SERVICE_ISUP = 5;
    public static final int DEFAULT_NI = 2;//NATIONAL, as default.

    private Mtp3() {
        // utility class
    }

    // //////////////////
    // Helper methods //
    // //////////////////
    public static final int dpc(byte[] sif, int shift) {
        int dpc = (sif[0 + shift] & 0xff | ((sif[1 + shift] & 0x3f) << 8));
        return dpc;
    }

    public static final int opc(byte[] sif, int shift) {
        int opc = ((sif[1 + shift] & 0xC0) >> 6) | ((sif[2 + shift] & 0xff) << 2) | ((sif[3 + shift] & 0x0f) << 10);
        return opc;
    }

    public static final int sls(byte[] sif, int shift) {
        int sls = (sif[3 + shift] & 0xf0) >>> 4;
        return sls;
    }    

    public static final int si(byte[] data) {
        
        int serviceIndicator = data[0] & 0x0f;
        return serviceIndicator;
    }
    
    public static final int ssi(byte[] data) {
        //see Q.704.14.2 
        int subserviceIndicator = (data[0] >> 4) & 0x0F;
        return subserviceIndicator;
    }
    
    public static void writeRoutingLabel(byte[] data, int si, int ssi, int sls, int dpc, int opc) {
        //see Q.704.14.2 
        writeRoutingLabel(0,data, si, ssi, sls, dpc, opc);
        
    }   
    public static void writeRoutingLabel(int shift, byte[] data, int si, int ssi, int sls, int dpc, int opc) {
        //see Q.704.14.2 
        data[0+shift] = (byte) (((ssi & 0x0F) << 4) | (si & 0x0F));
        data[1+shift] = (byte) dpc;
        data[2+shift] = (byte) (((dpc >> 8) & 0x3F) | ((opc & 0x03) << 6));
        data[3+shift] = (byte) (opc >> 2);
        data[4+shift] = (byte) (((opc >> 10) & 0x0F) | ((sls & 0x0F) << 4));
        
    } 
}
