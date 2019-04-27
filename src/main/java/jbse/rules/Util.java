package jbse.rules;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jbse.mem.Clause;
import jbse.mem.ClauseAssumeExpands;
import jbse.mem.Objekt;
import jbse.mem.State;
import jbse.mem.exc.FrozenStateException;
import jbse.val.ReferenceConcrete;
import jbse.val.ReferenceSymbolic;

public final class Util {
	//These must match the analogous definitions in SettingsParser.jj
	static final String MAX = "{MAX}";
	static final String ANY = "{R_ANY}";
	static final String REF = "{$REF}";
	static final String REFANY = "{$R_ANY}";
	static final String UP = "{UP}";
	static final String REGEX_ALLCHARS = "{°}";
	static final String REGEX_ENDLINE = "{EOL}";

	/**
	 * Makes a regular expression pattern from an origin expression
	 * in a rule.
	 * 
	 * @param s a {@link String}, the origin expression of a rule.
	 * @return a {@link Pattern} for {@code s} against which the 
	 *         origin strings contained in the objects can match.
	 */
	public static Pattern makeOriginPattern(String s) {
		return Pattern.compile(translateToOriginPattern(s));
	}
	
	/**
	 * Makes a {@link Pattern} for a relative origin expression in 
	 * a rule.
	 * 
	 * @param relativeExp a {@link String}, the relative origin expression 
	 *        in the rule.
	 * @param origin a {@link ReferenceSymbolic}, the origin w.r.t. 
	 *        we want to make {@code s} absolute.
	 * @return a {@link Pattern} for {@code s} where all the occurrences 
	 *         of {REF} and {UP} are resolved using {@code origin}.
	 */
	static Pattern makePatternRelative(String relativeExp, ReferenceSymbolic origin) {
		return Pattern.compile(translateToOriginPattern(translateRelativeToAbsolute(relativeExp, origin.asOriginString())));
	}
	
	/* TODO this is really ugly, but it works with the current 
	 * implementation of origins as strings. Improve it later to a 
	 * separate language. 
	 */
	private static String translateToOriginPattern(String absoluteExp) {
		String retVal = absoluteExp.replace(".", "\\."); 
		retVal = retVal.replace(ANY, "(.*)");
		retVal = retVal.replace(REGEX_ALLCHARS, "."); 
		retVal = retVal.replace("{", "\\{"); //this is for {ROOT}
		retVal = retVal.replace("}", "\\}"); //this also is for {ROOT}
		retVal = retVal.replace("[", "\\["); //this is for [<className>]
		retVal = retVal.replace("]", "\\]"); //this also is for [<className>]
		retVal = retVal.replace("$", "\\$"); //this is for names of inner classes
		retVal = retVal.replace(REGEX_ENDLINE, "$"); 
		return retVal;
	}

	/* TODO this also is really ugly, and it does not work with 
	 * multiple candidate origins for the same object.
	 */
	private static String translateRelativeToAbsolute(String relativeExp, String originString) {
		// replaces REF with ref.origin
		String retVal = relativeExp.replace(REF, originString);
		
		// eats all /whatever/UP pairs 
		String retValOld;
		do {
			retValOld = retVal;
			retVal = retVal.replaceFirst("\\.[^\\.]+\\.\\Q" + UP + "\\E", "");
		} while (!retVal.equals(retValOld));
		return retVal;
	}
	
	static String findAny(String pattern, ReferenceSymbolic origin) {
		final Pattern p = makeOriginPattern(pattern);
		final Matcher m = p.matcher(origin.asOriginString());
		if (m.matches() && m.pattern().pattern().startsWith("(.*)") && m.groupCount() >= 1) {
			final String valueForAny = m.group(1).replace(".","/");
			return valueForAny;
		} else {
			return null;
		}
	}
	
	static String specializeAny(String expression, String valueForAny) {
		return (valueForAny == null ? expression : expression.replace(REFANY, valueForAny));	
	}

	/**
	 * Searches for the actual parameter of a trigger rule.
	 * 
	 * @param r a {@link TriggerRule}.
	 * @param ref the {@link ReferenceSymbolic} that made fire 
	 *        {@code r}.
	 * @param state a {@link State}.
	 * @return the first {@link Objekt} in the heap of {@code state} 
	 *         whose origin matches the trigger parameter part 
	 *         of {@code r}, or {@code null} if none exists.
	 * @throws FrozenStateException if {@code state} is frozen.
	 */
	public static ReferenceConcrete getTriggerMethodParameterObject(TriggerRule r, ReferenceSymbolic ref, State state) throws FrozenStateException {
        final Iterable<Clause> pathCondition = state.getPathCondition(); //TODO the decision procedure already stores the path condition: eliminate dependence on state
        for (Clause c : pathCondition) {
            if (c instanceof ClauseAssumeExpands) {
                //gets the object and its position in the heap
                final ClauseAssumeExpands cExp = (ClauseAssumeExpands) c;
                final Long i = cExp.getHeapPosition();
                final Objekt o = cExp.getObjekt();
                
    			if (r.isTriggerMethodParameterObject(ref, o)){
    				return new ReferenceConcrete(i);
    			}
            }
        }
		return null;
	}

	//do not instantiate it!
	private Util() { }
}
