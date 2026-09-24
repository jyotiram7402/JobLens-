package com.joblens.api.user.domain;

import com.joblens.api.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.Locale;

/**
 * An account.
 *
 * <p>Holds a password <em>hash</em> and never a password. The field is
 * deliberately not exposed through any DTO, and {@link #toString()} is
 * overridden to make sure it cannot reach a log line by accident -- the default
 * record-style {@code toString} on an entity is a classic way to leak a hash
 * into an error message.
 *
 * <p>Email is stored lowercased and trimmed, which is what makes the unique
 * index case-insensitive without a functional index or the citext extension.
 * Normalising happens here, in one place, so no caller can create an account
 * that logging in will then fail to find.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private Role role = Role.USER;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** For JPA only. */
    protected User() {
    }

    private User(String email, String passwordHash, String firstName, String lastName) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = Role.USER;
        this.active = true;
    }

    /**
     * @param passwordHash an already-encoded hash. This class does no hashing:
     *                     handing it a raw password would be indistinguishable
     *                     at the call site from handing it a hash, so it only
     *                     ever accepts the latter.
     */
    public static User register(String email, String passwordHash, String firstName,
                                String lastName) {
        return new User(email, passwordHash, firstName, lastName);
    }

    /**
     * Lowercase with {@link Locale#ROOT} for the same reason names are: a
     * Turkish default locale would lowercase {@code I} to a dotless {@code ı}
     * and make an address unmatchable.
     */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public void changeName(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Never includes the password hash or the email. Entities end up in log
     * messages and exception text more often than anyone intends.
     */
    @Override
    public String toString() {
        return "User{id=%s}".formatted(getId());
    }
}
