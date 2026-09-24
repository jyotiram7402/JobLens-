/**
 * Authentication infrastructure: token issuing and verification, the servlet
 * filter that authenticates a request, and the handlers that render 401 and 403
 * in the application's standard error shape.
 *
 * <p>Separate from {@code user} on purpose. The user module owns accounts and
 * profiles; this package owns how a request proves who it is. Keeping them
 * apart means the answer to "how is a token verified?" is in one place, and a
 * later change -- refresh tokens, a denylist, OAuth -- happens here without
 * touching the domain.
 *
 * <p>Depends on {@code user} for {@code User} and {@code Role} only. Nothing in
 * {@code user} depends on this package except for reading the current
 * principal.
 */
package com.joblens.api.security;
