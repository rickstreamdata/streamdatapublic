/*
 * Copyright (c) 2016 Villu Ruusmann
 *
 * This file is part of JPMML-R
 *
 * JPMML-R is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-R is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-R.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.rexp;

import org.jpmml.evaluator.FloatEquivalence;
import org.jpmml.evaluator.PMMLEquivalence;
import org.junit.Test;

public class XGBoostConverterTest extends ConverterTest {

	public XGBoostConverterTest(){
		super(new FloatEquivalence(1));
	}

	@Test
	public void evaluateAutoNA() throws Exception {
		evaluate("XGBoost", "AutoNA");
	}

	@Test
	public void evaluateIris() throws Exception {
		evaluate("XGBoost", "Iris", new PMMLEquivalence(1e-6, 1e-6));
	}
}