/*
 * Copyright (c) 2004-2015 Universidade do Porto - Faculdade de Engenharia
 * Laboratório de Sistemas e Tecnologia Subaquática (LSTS)
 * All rights reserved.
 * Rua Dr. Roberto Frias s/n, sala I203, 4200-465 Porto, Portugal
 *
 * This file is part of Neptus, Command and Control Framework.
 *
 * Commercial Licence Usage
 * Licencees holding valid commercial Neptus licences may use this file
 * in accordance with the commercial licence agreement provided with the
 * Software or, alternatively, in accordance with the terms contained in a
 * written agreement between you and Universidade do Porto. For licensing
 * terms, conditions, and further information contact lsts@fe.up.pt.
 *
 * European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENCE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * https://www.lsts.pt/neptus/licence.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: Paulo Dias
 * 3 de Out de 2012
 */
package pt.lsts.neptus.loader.helper;


/**
 * Used in the bat and sh lauchers. Don't use Neptus log.
 * @author pdias
 *
 */
public class CheckJavaOSArch {
    
    public static String getOs() {
        String osName = System.getProperty("os.name").toLowerCase().replaceAll(" ", "");
        
        if (osName.contains("mac"))
            osName = "osx";
        if (osName.contains("win"))
            osName = "windows";
        String osArch = System.getProperty("os.arch");
        String arch = "x86";
        if (osArch.contains("64"))
            arch = "x64";
        
        return osName+"-"+arch;
    }
    /**
     * @param args
     */
    public static void main(String[] args) {
        System.out.println(getOs()); 
    }
}
