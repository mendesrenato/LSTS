/*
 * Copyright (c) 2004-2021 Universidade do Porto - Faculdade de Engenharia
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
 * Modified European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the Modified EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENCE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * https://github.com/LSTS/neptus/blob/develop/LICENSE.md
 * and http://ec.europa.eu/idabc/eupl.html.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: 
 * 2007/07/11
 */
package pt.lsts.neptus.util.conf;


/**
 * @author pdias
 *
 */
public class StringListValidator implements Validator {

	protected String[] list = new String[0];
	
	/**
	 * 
	 */
	public StringListValidator(String ... vals) {
		list = vals;
	}

	public String validate(Object newValue) {
		try {
			String comp = (String) newValue;
			
			for (String val : list)
			{
				if (val.equals(comp))
					return null;
			}
		} catch (Exception e) {
			return e.getMessage();
		}
		return "No valid value found.";
	}

	@Override
	public String validValuesDesc() {
		String ret = "";
		for (String val : list) {
			ret += val + ", ";
		}
		if (list.length >= 1)
			ret = ret.substring(0, ret.length()-2);
		return ret;
	}
}
