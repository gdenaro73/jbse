package jbse.mem;

import java.util.Arrays;

/**
 * A {@link Reference} to a concrete array which may not yet 
 * have a corresponding {@link Array} instance in the 
 * {@link Heap}. A {@link ReferenceArrayImmaterial} is always stored 
 * in an {@link Array} created by a (multi)anewarray with symbolic 
 * length values. When the container {@link Array} is accessed, it returns 
 * a {@link ReferenceArrayImmaterial}, which is in turns used to lazily 
 * create concrete arrays in a layer. A {@link ReferenceArrayImmaterial} always
 * refers to objects created after the start of the symbolic execution. 
 * A {@link ReferenceArrayImmaterial} shall not escape to the operand stack, 
 * it must be materialized whenever extracted from an array.
 * 
 * @author Pietro Braione
 */
public class ReferenceArrayImmaterial extends ReferenceConcrete {
	/** The type of the array. */
	final String arrayType;
	
	/** The lengths of the array's dimensions. */
	private Primitive[] arrayLength;
	
	public ReferenceArrayImmaterial(String arrayType, Primitive[] arrayLength) {
		super(Util.POS_UNKNOWN);
		this.arrayType = arrayType;
		this.arrayLength = arrayLength;
	}
	
	public String getArrayType() {
		return this.arrayType;
	}
	
	/** 
	 * Gets the length of the array.
	 * 
	 * @return a {@link Primitive}, the length of the 
	 *         array (i.e., of its first dimension). As an
	 *         example, if {@code this} is an
	 *         {@link ReferenceArrayImmaterial} satisfied by
	 *         an array with dimensions {@code [foo][bar]},
	 *         {@code this.getLength()} returns {@code foo}. 
	 *           
	 */
	public Primitive getLength() {
		return this.arrayLength[0];
	}
	
	/**
	 * Gets the next {@link ReferenceArrayImmaterial}.
	 *  
	 * @return an {@link ReferenceArrayImmaterial} for all the 
	 *         members of the array satisfying {@code this}.
	 *         As an example, if {@code this} is an
	 *         {@link ReferenceArrayImmaterial} satisfied by
	 *         an array with dimensions {@code [foo][bar]},
	 *         {@code this.next()} returns {@code [bar]}.
	 *         If {@code this} is a constraint satisfied by 
	 *         a monodimensional array, the method returns 
	 *         {@code null} (e.g., for the above example
	 *         {@code this.next().next() == null}).
	 *          
	 */
	public ReferenceArrayImmaterial next() {
		if (this.arrayLength.length > 1) {
			Primitive[] newLength = new Primitive[this.arrayLength.length - 1];
			boolean first = true;
			int i = 0;
			for (Primitive p : this.arrayLength) {
				if (first) {
					first = false;
				} else {
					newLength[i] = p;
				}
				i++;
			}
			return new ReferenceArrayImmaterial(this.arrayType, newLength);
		} else {
			return null;
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null) {
			return false;
		}
		if (this.getClass() != o.getClass()) {
			return false;
		}
		final ReferenceArrayImmaterial c = (ReferenceArrayImmaterial) o;
		if (c.arrayLength.length != this.arrayLength.length) {
			return false;
		}
		//heap position is unknown
		if (!this.arrayType.equals(c.arrayType)) {
			return false;
		}
		return Arrays.equals(this.arrayLength, c.arrayLength);
	}

	@Override
	public String toString() {
		String retVal = "{R<";
		for (Primitive p : this.arrayLength) {
			if (p == null) {
				retVal += "*";
			} else {
				retVal += p.toString();
			}
		}
		retVal += ">}";
		return retVal;
	}
}