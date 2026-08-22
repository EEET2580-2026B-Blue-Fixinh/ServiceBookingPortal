import { useState, useEffect, useRef } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import {
  DEMO_ACCOUNTS,
  SHOW_DEMO_ACCOUNT,
  GOOGLE_CLIENT_ID,
} from "../../config/env";
import { ROLES, ROUTES, SESSION_EXPIRED_MESSAGE } from "../../config/constants";
import { AuthLayout } from "../../components/auth/AuthLayout";
import { AuthIntro } from "../../components/auth/AuthIntro";
import { GoogleIcon, MaterialIcon } from "../../components/shared/icons";
import { isValidLoginIdentifier } from "../../utils/validators";
import { useAuth } from "../../context/AuthContext";
import {
  login as requestLogin,
  googleLogin as requestGoogleLogin,
} from "../../services/api/endpoints/authEndpoints";
import {
  consumeGoogleOAuthState,
  createGoogleAuthorizationUrl,
  googleRedirectUri,
} from "../../services/googleAuthService";

// Hidden rather than broken when a developer has not copied .env.example: without
// an address the buttons would fill blanks and the sign-in would fail confusingly.
const CAN_SHOW_DEMO_ACCOUNT =
  SHOW_DEMO_ACCOUNT && Boolean(DEMO_ACCOUNTS.customer.email);

export function LoginPage() {
  // Declared before the state below, which seeds itself from location.state.
  const location = useLocation();

  const [form, setForm] = useState({ identifier: "", password: "" });
  const [errors, setErrors] = useState({});
  const [showPassword, setShowPassword] = useState(false);
  // Seeded once from the redirect that sent us here, so a forced sign-out explains
  // itself in the alert this page already renders. handleChange clears it as soon
  // as the user starts typing.
  const [formError, setFormError] = useState(
    location.state?.sessionExpired ? SESSION_EXPIRED_MESSAGE : "",
  );
  const [submitting, setSubmitting] = useState(false);

  const { login: startSession } = useAuth();
  const navigate = useNavigate();
  const oauthProcessed = useRef(false); // Prevent double OAuth processing

  // Handle Google OAuth callback (Req 3.2.U1)
  useEffect(() => {
    const handleGoogleCallback = async () => {
      // Check if this is a Google OAuth redirect with an authorization code
      const urlParams = new URLSearchParams(window.location.search);
      const code = urlParams.get("code");
      const state = urlParams.get("state");
      const oauthError = urlParams.get("error");

      if (oauthError) {
        window.history.replaceState({}, "", window.location.pathname);
        setFormError(
          oauthError === "access_denied"
            ? "Google sign-in was cancelled."
            : "Google sign-in failed.",
        );
        return;
      }

      if (code && !oauthProcessed.current) {
        if (!consumeGoogleOAuthState(state)) {
          setFormError(
            "Google sign-in could not be verified. Please try again.",
          );
          return;
        }
        oauthProcessed.current = true; // Mark as processing immediately

        // Clean up URL immediately to prevent double submission
        window.history.replaceState({}, "", window.location.pathname);

        setSubmitting(true);
        setFormError("");

        try {
          // Exchange authorization code for JWT token
          const session = await requestGoogleLogin({
            authorizationCode: code,
            redirectUri: googleRedirectUri(),
          });

          // Start session immediately so user is authenticated
          startSession(session.accessToken, session.user, session.expiresIn);

          // Check if profile completion is required
          if (session.profileCompletionRequired) {
            // New Google user without address - redirect to profile completion
            // Session is already started, so they're authenticated
            navigate(ROUTES.PROFILE_COMPLETION, { replace: true });
            return;
          }

          // Existing user - redirect to dashboard
          const isAdmin = session.user?.role === ROLES.ADMIN;
          const home = isAdmin ? ROUTES.ADMIN : ROUTES.BOOKING;
          const intended = location.state?.from?.pathname;
          const canReturn =
            intended && intended.startsWith(ROUTES.ADMIN) === isAdmin;

          navigate(canReturn ? intended : home, { replace: true });
        } catch (error) {
          setFormError(
            error.message || "Google sign-in failed. Please try again.",
          );
        } finally {
          setSubmitting(false);
        }
      }
    };

    handleGoogleCallback();
  }, []); // Empty dependency array - only run once on mount

  const handleChange = (event) => {
    const { name, value } = event.target;
    setForm((prev) => ({ ...prev, [name]: value }));
    setErrors((prev) => (prev[name] ? { ...prev, [name]: undefined } : prev));
    setFormError("");
  };

  const handleFillDemoAccount = (role) => {
    setErrors({});
    setFormError("");
    const account =
      role === "admin" ? DEMO_ACCOUNTS.admin : DEMO_ACCOUNTS.customer;
    setForm({ identifier: account.email, password: account.password });
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    const nextErrors = {};
    if (!isValidLoginIdentifier(form.identifier)) {
      nextErrors.identifier = "Enter a valid email or phone number.";
    }
    if (!form.password) {
      nextErrors.password = "Enter your password.";
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    setSubmitting(true);
    setFormError("");
    try {
      const session = await requestLogin({
        identifier: form.identifier.trim(),
        password: form.password,
      });

      // expiresIn is the token's lifetime in seconds; the context turns it into the
      // deadline it enforces, so a forced sign-out can happen without an API call.
      startSession(session.accessToken, session.user, session.expiresIn);

      // Where this account belongs when it has nowhere else to be: /booking lists
      // the caller's own bookings, which an administrator does not have.
      const isAdmin = session.user?.role === ROLES.ADMIN;
      const home = isAdmin ? ROUTES.ADMIN : ROUTES.BOOKING;

      // Return them to whatever a route guard bounced them off — but only if this
      // account can actually go there. Someone signed out who tried /booking, then
      // signed in as an administrator, must not be sent back to a page the guards
      // would immediately bounce them off again.
      const intended = location.state?.from?.pathname;
      const canReturn =
        intended && intended.startsWith(ROUTES.ADMIN) === isAdmin;

      navigate(canReturn ? intended : home, { replace: true });
    } catch (error) {
      // The backend answers an unknown identifier and a wrong password
      // identically on purpose, so there is one message to show for both.
      setFormError(error.message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleGoogleLogin = () => {
    if (!GOOGLE_CLIENT_ID) {
      setFormError("Google Sign-In is not configured.");
      return;
    }

    // Redirect to Google's OAuth 2.0 authorization endpoint (Req 3.2.U1)
    window.location.href = createGoogleAuthorizationUrl();
  };

  const handleForgotPassword = () => {
    // Password reset flow will be implemented in a future task
  };

  return (
    <AuthLayout brand={<AuthIntro />}>
      <h2 className="auth-title">Sign in</h2>
      <p className="auth-subtitle">
        Use the email or phone number on your Fixinh account.
      </p>

      <form onSubmit={handleSubmit} noValidate>
        {formError && (
          <p className="auth-form-error" role="alert">
            {formError}
          </p>
        )}

        <div className="auth-field">
          <label htmlFor="identifier">Email or phone number</label>
          <input
            id="identifier"
            name="identifier"
            type="text"
            autoComplete="username"
            placeholder="Please enter your email or phone number"
            value={form.identifier}
            onChange={handleChange}
            aria-invalid={Boolean(errors.identifier)}
            className={errors.identifier ? "auth-input-invalid" : ""}
            required
          />
          {errors.identifier && (
            <p className="auth-error-text">{errors.identifier}</p>
          )}
        </div>

        <div className="auth-field">
          <div className="auth-label-row">
            <label htmlFor="password">Password</label>
            <button
              type="button"
              className="auth-forgot-link"
              onClick={handleForgotPassword}
            >
              Forgot?
            </button>
          </div>
          <div className="auth-password-wrap">
            <input
              id="password"
              name="password"
              type={showPassword ? "text" : "password"}
              autoComplete="current-password"
              placeholder="Please enter your password"
              value={form.password}
              onChange={handleChange}
              aria-invalid={Boolean(errors.password)}
              className={errors.password ? "auth-input-invalid" : ""}
              required
            />
            <button
              type="button"
              className="auth-password-toggle"
              onClick={() => setShowPassword((prev) => !prev)}
              aria-label={showPassword ? "Hide password" : "Show password"}
            >
              <MaterialIcon
                name={showPassword ? "visibility_off" : "visibility"}
              />
            </button>
          </div>
          {errors.password && (
            <p className="auth-error-text">{errors.password}</p>
          )}
        </div>

        <button className="auth-submit" type="submit" disabled={submitting}>
          {submitting ? "Signing in…" : "Sign in"}
        </button>
      </form>

      <div className="auth-divider">or</div>

      <button
        type="button"
        className="auth-google-btn"
        onClick={handleGoogleLogin}
      >
        <GoogleIcon />
        Continue with Google
      </button>

      <p className="auth-switch">
        New to Fixinh? <Link to="/register">Create an account</Link>
      </p>

      {CAN_SHOW_DEMO_ACCOUNT && (
        <div className="auth-demo-box">
          <span className="auth-demo-label">Demo accounts</span>
          <div className="auth-demo-buttons">
            <button
              type="button"
              className="auth-demo-btn"
              onClick={() => handleFillDemoAccount("customer")}
            >
              <MaterialIcon name="person" />
              Customer
            </button>
            <button
              type="button"
              className="auth-demo-btn"
              onClick={() => handleFillDemoAccount("admin")}
            >
              <MaterialIcon name="admin_panel_settings" />
              Administrator
            </button>
          </div>
        </div>
      )}
    </AuthLayout>
  );
}
