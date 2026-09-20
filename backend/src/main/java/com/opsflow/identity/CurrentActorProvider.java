/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity;

/**
 * The authenticated principal for the current request, including its roles.
 *
 * <p>Only meaningful inside an authenticated HTTP request. Outside one — on a batch thread, in a
 * scheduled task — {@link #requireActor()} throws rather than returning an empty
 * {@link Actor}, because a principal with no roles is indistinguishable from one whose roles
 * failed to load, and the difference matters to every authorization check.
 *
 * <p>Asynchronous work that needs authority carries it explicitly and durably instead.
 */
public interface CurrentActorProvider {

    /**
     * @throws IllegalStateException when there is no authenticated request in scope
     */
    Actor requireActor();
}
