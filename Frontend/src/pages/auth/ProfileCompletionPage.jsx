import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ROUTES, ROLES } from "../../config/constants";
import { AuthLayout } from "../../components/auth/AuthLayout";
import { AuthIntro } from "../../components/auth/AuthIntro";
import { SelectField } from "../../components/shared/form/SelectField/SelectField";
import { useVietnamAddress } from "../../hooks/useVietnamAddress";
import { useAuth } from "../../context/AuthContext";
import { completeAddress } from "../../services/api/endpoints/customerEndpoints";

/**
 * Profile completion for new Google Sign-In users (Req 3.2.U3).
 *
 * A Google user who just signed in for the first time has an account but no address,
 * which means they cannot book a service yet. This page collects the address and
 * sends it to the backend to complete the profile.
 */
export function ProfileCompletionPage() {
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const {
    city,
    cityCode,
    ward,
    provinces,
    wards,
    provincesStatus,
    wardsStatus,
    cityPlaceholder,
    wardPlaceholder,
    handleCityChange,
    handleWardChange,
  } = useVietnamAddress();

  const [form, setForm] = useState({ streetNumber: "", streetName: "" });
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  // province names as strings for SelectField
  const provinceNames = provinces.map((p) => p.provinceName);
  const wardNames = wards.map((w) => w.name);

  const handleChange = (event) => {
    const { name, value } = event.target;
    setForm((prev) => ({ ...prev, [name]: value }));
    setErrors((prev) => (prev[name] ? { ...prev, [name]: undefined } : prev));
    setFormError("");
  };

  const handleCitySelect = (value) => {
    handleCityChange(value);
    setErrors((prev) => ({ ...prev, city: undefined }));
    setFormError("");
  };

  const handleWardSelect = (value) => {
    handleWardChange(value);
    setErrors((prev) => ({ ...prev, ward: undefined }));
    setFormError("");
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    const nextErrors = {};
    if (!form.streetNumber.trim())
      nextErrors.streetNumber = "Street number is required.";
    if (!form.streetName.trim())
      nextErrors.streetName = "Street name is required.";
    if (!ward) nextErrors.ward = "Please choose a ward.";
    if (!cityCode) nextErrors.city = "Please choose a city.";

    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    setSubmitting(true);
    setFormError("");

    try {
      await completeAddress({
        streetNumber: form.streetNumber.trim(),
        streetName: form.streetName.trim(),
        ward: ward,
        provinceCode: cityCode,
      });

      const isAdmin = user?.role === ROLES.ADMIN;
      navigate(isAdmin ? ROUTES.ADMIN : ROUTES.BOOKING, { replace: true });
    } catch (error) {
      setFormError(
        error.message || "Failed to complete profile. Please try again.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout brand={<AuthIntro />}>
      <h2 className="auth-title">Complete your profile</h2>
      <p className="auth-subtitle">
        We need your address to connect you with nearby service providers.
      </p>

      <form onSubmit={handleSubmit} noValidate>
        {formError && (
          <p className="auth-form-error" role="alert">
            {formError}
          </p>
        )}

        <div className="auth-field">
          <label htmlFor="streetNumber">Street number</label>
          <input
            id="streetNumber"
            name="streetNumber"
            type="text"
            autoComplete="address-line1"
            placeholder="e.g. 123"
            value={form.streetNumber}
            onChange={handleChange}
            aria-invalid={Boolean(errors.streetNumber)}
            className={errors.streetNumber ? "auth-input-invalid" : ""}
            required
          />
          {errors.streetNumber && (
            <p className="auth-error-text">{errors.streetNumber}</p>
          )}
        </div>

        <div className="auth-field">
          <label htmlFor="streetName">Street name</label>
          <input
            id="streetName"
            name="streetName"
            type="text"
            autoComplete="address-line2"
            placeholder="e.g. Nguyen Trai"
            value={form.streetName}
            onChange={handleChange}
            aria-invalid={Boolean(errors.streetName)}
            className={errors.streetName ? "auth-input-invalid" : ""}
            required
          />
          {errors.streetName && (
            <p className="auth-error-text">{errors.streetName}</p>
          )}
        </div>

        <div className="auth-field">
          <label htmlFor="city">City / Province</label>
          <SelectField
            id="city"
            value={city}
            onChange={handleCitySelect}
            options={provinceNames}
            placeholder={cityPlaceholder}
            disabled={provincesStatus !== "ready"}
          />
          {errors.city && <p className="auth-error-text">{errors.city}</p>}
        </div>

        <div className="auth-field">
          <label htmlFor="ward">Ward</label>
          <SelectField
            id="ward"
            value={ward}
            onChange={handleWardSelect}
            options={wardNames}
            placeholder={wardPlaceholder}
            disabled={!city || wardsStatus !== "ready"}
          />
          {errors.ward && <p className="auth-error-text">{errors.ward}</p>}
        </div>

        <button
          className="auth-submit"
          type="submit"
          disabled={submitting || provincesStatus !== "ready"}
        >
          {submitting ? "Completing profile…" : "Continue"}
        </button>
      </form>

      <p className="auth-signed-in-status">
        Signed in as <strong>{user?.email}</strong>
      </p>
      <button
        type="button"
        className="auth-forgot-link auth-sign-out"
        onClick={logout}
      >
        Sign out and use another account
      </button>
    </AuthLayout>
  );
}
