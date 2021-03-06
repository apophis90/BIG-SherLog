package com.big.sherlog.integration;

import java.io.IOException;

import com.big.sherlog.logger.LoggerProvider;
import javassist.*;

/**
 * This class provides the core implementation of how a certain method is
 * identified, removed, transformed and re-attached using
 * <a href="http://jboss-javassist.github.io/javassist/">Javassist</a>. The
 * decision about what kind of transformation is actually performed is left to
 * concrete implementations.<br/>
 * 
 * Created by patrick.kleindienst on 01.06.2015.
 * 
 * @author patrick.kleindienst
 */
public abstract class BaseCodeIntegrator {

	// #####################################################
	// # STATIC MEMBERS #
	// #####################################################

	/**
	 * path to static {@link org.apache.log4j.Logger} member of
	 * {@link com.big.sherlog.logger.LoggerProvider} class.
	 */
	protected static final String PROVIDED_LOGGER = LoggerProvider.class.getName() + ".LOGGER";

	// #####################################################
	// # INSTANCE METHODS #
	// #####################################################

	/**
	 * This abstract method defines a contract for {@link BaseCodeIntegrator}
	 * implementations and leaves the decision of what exactly should be done
	 * with a method during a transformation to the client.
	 *
	 * @param ctMethod
	 *            method to be changed
	 * @return the modified {@link CtMethod} instance
	 */
	protected abstract CtMethod enhanceMethodCode(CtMethod ctMethod);

	/**
	 * This method is where the transformations are actually induced. Delegating
	 * to {@link BaseCodeIntegrator#enhanceMethodCode}, the method is removed
	 * and re-attached after transformation. Finally, the plain bytecode of the
	 * customized is returned for being used as the new definition of a class.
	 *
	 * @param className
	 *            the class to be transformed
	 * @param methodName
	 *            the method to be transformed
	 * @param methodSignature
	 *            the method's signature for further differentiation in case of
	 *            overloading
	 * @param classLoader
	 *            the class loader belonging to the class
	 * @param bytes
	 *            the original bytecode of the class
	 * @return the modified bytecode after transformation, <code>null</code> if
	 *         transformation fails for some reason
	 */
	public byte[] performCodeIntegration(String className, String methodName, String methodSignature, ClassLoader classLoader, byte[] bytes) {
		className = className.replace("/", ".");
		ClassPool classPool = new ClassPool(true);
		classPool.appendClassPath(new LoaderClassPath(classLoader));
		classPool.appendClassPath(new ByteArrayClassPath(className, bytes));

		try {
			CtClass ctClass = classPool.get(className);

			if (!searchAndReplaceMethod(ctClass, methodName, methodSignature)) {
				if (methodSignature == null) {
					LoggerProvider.LOGGER.error("Could not find method for name: " + methodName);
				} else {
					LoggerProvider.LOGGER.error("Could not find method for name: " + methodName + " (Signature: " + methodSignature + ")");
				}
			}
			return ctClass.toBytecode();
		} catch (NotFoundException e) {
			e.printStackTrace();
		} catch (CannotCompileException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 *
	 * Convenience method that performs the task of stepping through a certain
	 * class and triggers the instrumentation of the desired method.
	 *
	 * @param ctClass
	 *            The class containing a certain method
	 * @param methodName
	 *            The name of the method that should be instrumented
	 * @param methodSignature
	 *            The optional signature of the method
	 * @return true if the specified methods exists, false if not
	 * @throws NotFoundException
	 *             when the method could not be removed
	 * @throws CannotCompileException
	 *             when re-attaching the instrumented method fails
	 */
	private boolean searchAndReplaceMethod(CtClass ctClass, String methodName, String methodSignature) throws NotFoundException, CannotCompileException {
		boolean atLeastOneMethodFound = false;
		for (CtMethod ctMethod : ctClass.getDeclaredMethods()) {
			if (ctMethod.getName().equalsIgnoreCase(methodName)) {
				if (methodSignature != null && ctMethod.getSignature().equals(methodSignature)) {
					ctClass.removeMethod(ctMethod);
					ctClass.addMethod(enhanceMethodCode(ctMethod));
					atLeastOneMethodFound = true;
				} else if (methodSignature == null) {
					ctClass.removeMethod(ctMethod);
					ctClass.addMethod(enhanceMethodCode(ctMethod));
					atLeastOneMethodFound = true;
				}
			}
		}
		return atLeastOneMethodFound;
	}

}
