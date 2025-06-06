/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.text.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.BreakIterator;


/**
 * @since 3.0
 */
public class BreakIteratorTest {

	protected BreakIterator fBreakIterator;

	public void assertNextPositions(CharSequence ci, int position) {
		assertNextPositions(ci, new int[] {position});
	}

	public void assertNextPositions(CharSequence ci, int[] positions) {
		fBreakIterator.setText(ci.toString());

		// test next()
		for (int position : positions) {
			int pos= fBreakIterator.next();
			assertEquals(position, pos);
		}

		// test following()
		int idx= 0;
		for (int position : positions) {
			while (idx < position) {
				if (!illegalPos(ci, idx))
					assertEquals(position, fBreakIterator.following(idx));
				idx++;
			}
		}

	}

	/**
	 * Check if we are in a multi-byte delimiter.
	 *
	 * @param seq the sequence
	 * @param idx the index
	 * @return <code>true</code> if position is illegal
	 */
	private boolean illegalPos(CharSequence seq, int idx) {
		String DELIMS= "\n\r";
		if (idx == 0 || idx == seq.length())
			return false;
		char one= seq.charAt(idx - 1);
		char two= seq.charAt(idx);
		return one != two && DELIMS.indexOf(one) != -1 && DELIMS.indexOf(two) != -1;
	}

	public void assertPreviousPositions(CharSequence ci, int position) {
		assertPreviousPositions(ci, new int[] {position});
	}

	public void assertPreviousPositions(CharSequence ci, int[] positions) {
		fBreakIterator.setText(ci.toString());
		fBreakIterator.last();

		for (int i = positions.length - 1; i >= 0; i--) {
			int pos= fBreakIterator.previous();
			assertEquals(positions[i], pos);
		}

		// test preceding()
		int idx= ci.length();
		for (int i = positions.length - 1; i >= 0; i--) {
			int position= positions[i];
			while (idx > position) {
				if (!illegalPos(ci, idx))
					assertEquals(position, fBreakIterator.preceding(idx));
				idx--;
			}
		}
	}

}
