package com.prishtha.mvp.identity.internal.util.constant;

/** Validation rules from SRS ID-FR-01 (shared by signup, reset, change password). */
public final class AuthValidationConstant {
    private AuthValidationConstant() {}

    public static final String BD_PHONE_PATTERN = "^(?:\\+8801|8801|01)[3-9]\\d{8}$";
    public static final String BD_PHONE_MESSAGE = "Phone must be a valid Bangladeshi mobile number";

    public static final String PASSWORD_PATTERN =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$";
    public static final String PASSWORD_MESSAGE =
            "Password must be at least 8 characters with an upper-case letter, a lower-case letter, a digit and a special character";
}
