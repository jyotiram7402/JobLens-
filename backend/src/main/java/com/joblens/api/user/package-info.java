/**
 * Accounts, credentials and the career profile a job is matched against.
 *
 * <p>Implemented in roadmap step 4.
 *
 * <pre>
 * user/
 *   AuthController.java        public: register, login
 *   AuthService.java           registration, credential verification
 *   UserController.java        authenticated: /users/me and the profile
 *   UserService.java           profile read and replace
 *   UserRepository.java        accounts
 *   UserProfileRepository.java profiles
 *   domain/                    User, UserProfile, Role, RemotePreference
 *   dto/                       request and response records
 *   exception/                 domain failures, mapped to HTTP by ErrorCode
 * </pre>
 *
 * <p>Token issuing and verification live in {@code com.joblens.api.security},
 * not here: they are infrastructure shared with the filter chain, and this
 * module should not be the place anyone looks to change how a signature is
 * checked.
 *
 * <p>Two rules this module exists to enforce:
 *
 * <ol>
 *   <li><b>A raw password never leaves {@code AuthService}.</b> It arrives on a
 *       request DTO, is hashed, and is not stored, returned or logged.</li>
 *   <li><b>The acting user's id always comes from the security context.</b> No
 *       endpoint accepts a user id, so one user cannot address another's data.</li>
 * </ol>
 */
package com.joblens.api.user;
