package jbse.algo;

import static jbse.algo.BytecodeData_1KME.Kind.kind;
import static jbse.algo.Util.checkOverridingMethodFits;
import static jbse.algo.Util.exitFromAlgorithm;
import static jbse.algo.Util.failExecution;
import static jbse.algo.Util.lookupMethodImpl;
import static jbse.algo.Util.lookupMethodImplOverriding;
import static jbse.algo.Util.throwNew;
import static jbse.bc.ClassLoaders.CLASSLOADER_APP;
import static jbse.bc.Signatures.ILLEGAL_ACCESS_ERROR;
import static jbse.bc.Signatures.INCOMPATIBLE_CLASS_CHANGE_ERROR;
import static jbse.bc.Signatures.NO_SUCH_METHOD_ERROR;
import static jbse.bc.Signatures.NULL_POINTER_EXCEPTION;
import static jbse.common.Type.parametersNumber;

import java.util.function.Supplier;

import jbse.algo.exc.BaseUnsupportedException;
import jbse.algo.exc.CannotManageStateException;
import jbse.algo.exc.MetaUnsupportedException;
import jbse.algo.exc.NotYetImplementedException;
import jbse.bc.ClassFile;
import jbse.bc.ClassHierarchy;
import jbse.bc.Signature;
import jbse.bc.exc.BadClassFileVersionException;
import jbse.bc.exc.ClassFileIllFormedException;
import jbse.bc.exc.ClassFileNotAccessibleException;
import jbse.bc.exc.ClassFileNotFoundException;
import jbse.bc.exc.IncompatibleClassFileException;
import jbse.bc.exc.MethodAbstractException;
import jbse.bc.exc.MethodNotAccessibleException;
import jbse.bc.exc.MethodNotFoundException;
import jbse.bc.exc.PleaseLoadClassException;
import jbse.bc.exc.WrongClassNameException;
import jbse.common.exc.ClasspathException;
import jbse.common.exc.InvalidInputException;
import jbse.mem.State;
import jbse.mem.exc.FrozenStateException;
import jbse.mem.exc.ThreadStackEmptyException;
import jbse.tree.DecisionAlternative_NONE;
import jbse.val.Reference;

/**
 * Abstract algorithm for the invoke* bytecodes
 * (invoke[interface/special/static/virtual]).
 * 
 * @author Pietro Braione
 *
 */
//TODO this class was born when (JVMS 2nd edition) the four invoke bytecodes were not much different, and sharing the implementation made sense; now it should be split in four subclasses. 
public abstract class Algo_INVOKEX_Abstract extends Algorithm<
BytecodeData_1KME,
DecisionAlternative_NONE,
StrategyDecide<DecisionAlternative_NONE>, 
StrategyRefine<DecisionAlternative_NONE>, 
StrategyUpdate<DecisionAlternative_NONE>> {

    protected final boolean isInterface; //set by the constructor
    protected final boolean isSpecial; //set by the constructor
    protected final boolean isStatic; //set by the constructor

    public Algo_INVOKEX_Abstract(boolean isInterface, boolean isSpecial, boolean isStatic) {
        this.isInterface = isInterface;
        this.isSpecial = isSpecial;
        this.isStatic = isStatic;
    }

    protected ClassFile methodResolvedClass; //set by cooking methods (resolveMethod)
    protected ClassFile methodImplClass; //set by cooking methods (findImpl / findOverridingImpl)
    protected Signature methodImplSignature; //set by cooking methods (findImpl / findOverridingImpl)
    protected boolean isMethodImplSignaturePolymorphic; //set by cooking methods (findImpl / findOverridingImpl)
    protected boolean isMethodImplNative; //set by cooking methods (findImpl / findOverridingImpl)

    @Override
    protected final Supplier<BytecodeData_1KME> bytecodeData() {
        return () -> BytecodeData_1KME.withMethod(kind(this.isInterface, this.isSpecial, this.isStatic)).get();
    }
    
    @Override
    protected final Supplier<Integer> numOperands() {
        return () -> {
            return parametersNumber(this.data.signature().getDescriptor(), this.isStatic);
        };
    }

    protected final void resolveMethod(State state) 
    throws ClassFileNotFoundException, ClassFileIllFormedException, BadClassFileVersionException, 
    WrongClassNameException, ClassFileNotAccessibleException, PleaseLoadClassException, 
    IncompatibleClassFileException, MethodNotFoundException, MethodNotAccessibleException,
    ThreadStackEmptyException, InvalidInputException {
        final ClassFile currentClass = state.getCurrentClass();
        if (this.data.signature().getClassName() == null) {
            //signature with no class: skips resolution
            this.methodResolvedClass = null;
        } else {
            this.methodResolvedClass = state.getClassHierarchy().resolveMethod(currentClass, this.data.signature(), this.isInterface, state.bypassStandardLoading());
        }
    }
    
    protected final void check(State state) 
    throws InterruptException, CannotManageStateException, ClasspathException, ThreadStackEmptyException, FrozenStateException {
        if (this.methodResolvedClass == null) {
            return;
        }
        
        try {
            if (this.isInterface) {
                //checks for invokeinterface
                
                //TODO the resolved method must not be an instance initialization method, or the class or interface initialization method: what should we do if it is???
                
                //the first linking exception pertains to method resolution
                
                //second linking exception
                if (this.methodResolvedClass.isMethodStatic(this.data.signature()) || this.methodResolvedClass.isMethodPrivate(this.data.signature())) {
                    throwNew(state, this.ctx.getCalculator(), INCOMPATIBLE_CLASS_CHANGE_ERROR);
                    exitFromAlgorithm();
                }
                
                //first run-time exception
                final Reference receiver = state.peekReceiverArg(this.data.signature());
                if (state.isNull(receiver)) {
                    throwNew(state, this.ctx.getCalculator(), NULL_POINTER_EXCEPTION);
                    exitFromAlgorithm();
                }
                
                //second run-time exception
                if (!state.getObject(receiver).getType().isSubclass(this.methodResolvedClass)) {
                    throwNew(state, this.ctx.getCalculator(), INCOMPATIBLE_CLASS_CHANGE_ERROR);
                    exitFromAlgorithm();
                }
                
                //the third, fourth, fifth, sixth and seventh run-time exception pertain to lookup of method implementation
            } else if (this.isSpecial) {
                //checks for invokespecial
                
                //the first linking exception pertains to method resolution
                
                //second linking exception
                if ("<init>".equals(this.data.signature().getName()) &&
                    !this.methodResolvedClass.getClassName().equals(this.data.signature().getClassName())) {
                    throwNew(state, this.ctx.getCalculator(), NO_SUCH_METHOD_ERROR);
                    exitFromAlgorithm();
                }
                
                //third linking exception
                if (this.methodResolvedClass.isMethodStatic(this.data.signature())) {
                    throwNew(state, this.ctx.getCalculator(), INCOMPATIBLE_CLASS_CHANGE_ERROR);
                    exitFromAlgorithm();
                }
                
                //first run-time exception
                final Reference receiver = state.peekReceiverArg(this.data.signature());
                if (state.isNull(receiver)) {
                    throwNew(state, this.ctx.getCalculator(), NULL_POINTER_EXCEPTION);
                    exitFromAlgorithm();
                }
                
                //second run-time exception (identical to second run-time exception of invokevirtual case)
                final ClassFile currentClass = state.getCurrentClass();
                if (this.methodResolvedClass.isMethodProtected(this.data.signature()) &&
                	currentClass.isSubclass(this.methodResolvedClass)) {
                    final boolean sameRuntimePackage = (currentClass.getDefiningClassLoader() == this.methodResolvedClass.getDefiningClassLoader() && currentClass.getPackageName().equals(this.methodResolvedClass.getPackageName()));
                    final ClassFile receiverClass = state.getObject(receiver).getType();                    
                    if (!sameRuntimePackage && !receiverClass.isSubclass(currentClass)) {
                        throwNew(state, this.ctx.getCalculator(), ILLEGAL_ACCESS_ERROR);
                        exitFromAlgorithm();
                    }
                }
                
                //the third, fifth and sixth run-time exceptions pertain to lookup of method implementation                
                //the fourth run-time exception is not raised by JBSE (natives)
            } else if (this.isStatic) {
                //checks for invokestatic
                
                //TODO the resolved method must not be an instance initialization method, or the class or interface initialization method: what should we do if it is???
                
                //the first linking exception pertains to method resolution
                
                //second linking exception
                if (!this.methodResolvedClass.isMethodStatic(this.data.signature())) {
                    throwNew(state, this.ctx.getCalculator(), INCOMPATIBLE_CLASS_CHANGE_ERROR);
                    exitFromAlgorithm();
                }
                
                //the first run-time exception pertains to class/interface initialization
                //the second run-time exception is not raised by JBSE (natives)
            } else {            
                //checks for invokevirtual
                
                //TODO the resolved method must not be an instance initialization method, or the class or interface initialization method: what should we do if it is???
                
                //the first linking exception pertains to method resolution
                
                //second linking exception
                if (this.methodResolvedClass.isMethodStatic(this.data.signature())) {
                    throwNew(state, this.ctx.getCalculator(), INCOMPATIBLE_CLASS_CHANGE_ERROR);
                    exitFromAlgorithm();
                }
                
                //the third linking exception pertains to method type resolution
                
                //first run-time exception
                final Reference receiver = state.peekReceiverArg(this.data.signature());
                if (state.isNull(receiver)) {
                    throwNew(state, this.ctx.getCalculator(), NULL_POINTER_EXCEPTION);
                    exitFromAlgorithm();
                }
                
                //second run-time exception (identical to second run-time exception of invokespecial case)
                final ClassFile currentClass = state.getCurrentClass();
                if (this.methodResolvedClass.isMethodProtected(this.data.signature()) &&
                	currentClass.isSubclass(this.methodResolvedClass)) {
                    final boolean sameRuntimePackage = (currentClass.getDefiningClassLoader() == this.methodResolvedClass.getDefiningClassLoader() && currentClass.getPackageName().equals(this.methodResolvedClass.getPackageName()));
                    final ClassFile receiverClass = state.getObject(receiver).getType();                    
                    if (!sameRuntimePackage && !receiverClass.isSubclass(currentClass)) {
                        throwNew(state, this.ctx.getCalculator(), ILLEGAL_ACCESS_ERROR);
                        exitFromAlgorithm();
                    }
                }
                
                //the third, fourth, fifth and sixth run-time exception pertain to lookup of method implementation
                
                //the seventh and eighth run-time exceptions pertain to the code after method type resolution
            }            
        } catch (MethodNotFoundException | InvalidInputException e) {
            //this should never happen after resolution
            failExecution(e);
        }
    }

    protected final void findImpl(State state) 
    throws IncompatibleClassFileException, MethodNotAccessibleException, 
    MethodAbstractException, InterruptException, ThreadStackEmptyException, 
    FrozenStateException, InvalidInputException {
        if (this.methodResolvedClass == null) {
            this.methodImplClass = null;
            this.methodImplSignature = this.data.signature();
            this.isMethodImplSignaturePolymorphic = false;
            return;
        }
        
        try {
            final boolean isVirtualInterface = !this.isStatic && !this.isSpecial;
            final ClassFile receiverClass;
            if (isVirtualInterface) {
                final Reference thisRef = state.peekReceiverArg(this.data.signature());
                receiverClass = state.getObject(thisRef).getType();
            } else {
                receiverClass = null;
            }
            this.methodImplClass = 
                lookupMethodImpl(state, 
                                 this.methodResolvedClass, 
                                 this.data.signature(),
                                 this.isInterface, 
                                 this.isSpecial, 
                                 this.isStatic,
                                 receiverClass);
            this.methodImplSignature = 
                new Signature(this.methodImplClass.getClassName(), 
                              this.data.signature().getDescriptor(), 
                              this.data.signature().getName());
            this.isMethodImplSignaturePolymorphic = this.methodImplClass.isMethodSignaturePolymorphic(this.methodImplSignature);
            this.isMethodImplNative = this.methodImplClass.isMethodNative(this.methodImplSignature);
        } catch (MethodNotFoundException e) {
            this.methodImplClass = null;
            this.methodImplSignature = null;
            this.isMethodImplSignaturePolymorphic = false;
            this.isMethodImplNative = false;
        }
    }

    protected final void findOverridingImpl(State state)
    throws BaseUnsupportedException, MetaUnsupportedException, NotYetImplementedException, InterruptException, 
    ClasspathException, ThreadStackEmptyException, InvalidInputException {
        if (this.methodImplSignature == null || this.isMethodImplSignaturePolymorphic) {
            return; //no implementation to override, or method is signature polymorphic (cannot be overridden!)
        }
        
        final Signature methodSignatureOverriding = lookupMethodImplOverriding(state, this.ctx, this.methodImplClass, this.methodImplSignature, this.isInterface, this.isSpecial, this.isStatic, this.isMethodImplNative);
        if (methodSignatureOverriding == null) {
            return;
        }

        try {
            final ClassHierarchy hier = state.getClassHierarchy();
            final ClassFile classFileMethodOverriding = hier.getClassFileClassArray(CLASSLOADER_APP, methodSignatureOverriding.getClassName());
            checkOverridingMethodFits(state, this.methodImplClass, this.methodImplSignature, classFileMethodOverriding, methodSignatureOverriding);
            this.methodImplClass = classFileMethodOverriding;
            this.methodImplSignature = methodSignatureOverriding;
            this.isMethodImplSignaturePolymorphic = this.methodImplClass.isMethodSignaturePolymorphic(this.methodImplSignature);
        } catch (MethodNotFoundException e) {
            throw new BaseUnsupportedException(e);
        }
    }
}
