package com.prishtha.mvp.identity.internal.util.constant;

public final class IdentityRouteConstant {
    private IdentityRouteConstant() {}

    public static final String AUTH_BASE_PATH = "/api/v1/auth";
    public static final String SIGN_UP = "/signup";
    public static final String VERIFY_OTP = "/verify-otp";
    public static final String LOGIN = "/login";
    public static final String REFRESH = "/refresh";
    public static final String LOGOUT = "/logout";
    public static final String SOCIAL_GOOGLE = "/social/google";
    public static final String MFA_VERIFY = "/mfa/verify";
    public static final String MFA_ENABLE = "/mfa/enable";
    public static final String MFA_DISABLE = "/mfa/disable";
    public static final String PASSWORD_FORGOT = "/password/forgot";
    public static final String PASSWORD_RESET = "/password/reset";
    public static final String PASSWORD_CHANGE = "/password/change";
    public static final String SESSIONS = "/sessions";
    public static final String SECURITY_LOG = "/security-log";
}
