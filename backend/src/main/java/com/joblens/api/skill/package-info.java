/**
 * The shared skill vocabulary.
 *
 * <p>Its own module because two domains reference it: a user profile claims
 * skills, a job asks for them, and matching compares the two. Putting it inside
 * either one would force the other to depend on a module it has no business
 * knowing about.
 *
 * <p>Implemented in roadmap step 7, when matching first needed both sides to
 * mean the same thing by "Java".
 *
 * <pre>
 * skill/
 *   SkillService.java     find-or-create, race-tolerant
 *   SkillRepository.java  persistence
 *   domain/Skill.java     name + normalized identity
 * </pre>
 *
 * <p>Depends on {@code common} only. Nothing here knows about users or jobs.
 */
package com.joblens.api.skill;
