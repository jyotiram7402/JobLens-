/**
 * Informing a user about something they asked to be told about.
 *
 * <p>Not scheduled in the V1 roadmap -- it is a V2 concern (saved searches with
 * alerts). The boundary exists so that when notifications arrive they land in
 * their own module instead of being sprinkled through job and tracking code,
 * which is how notification logic usually becomes impossible to remove.
 *
 * <p>This package holds no code by design.
 */
package com.joblens.api.notification;
