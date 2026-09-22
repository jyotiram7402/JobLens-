/**
 * Code shared by every domain module: error handling, response shapes, the base
 * entity and web plumbing.
 *
 * <p>This is a shared kernel, and it only works if it stays small. Two rules
 * keep it that way:
 *
 * <ol>
 *   <li><b>{@code common} depends on nothing.</b> It must never import from
 *       {@code user}, {@code company}, {@code job} or any other module. A
 *       dependency in that direction turns the kernel into a cycle and the
 *       monolith into a ball of mud.</li>
 *   <li><b>Shared means used by more than one module.</b> Code used by exactly
 *       one module belongs in that module, however generic it looks.</li>
 * </ol>
 */
package com.joblens.api.common;
