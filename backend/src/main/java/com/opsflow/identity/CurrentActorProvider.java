/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity;

/**
 * Off-request this throws rather than returning an empty {@link Actor}: a principal with no roles
 * is indistinguishable from one whose roles failed to load, and every check would pass the wrong
 * way.
 */
public interface CurrentActorProvider {

    Actor requireActor();
}
