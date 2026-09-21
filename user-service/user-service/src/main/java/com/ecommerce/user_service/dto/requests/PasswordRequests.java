package com.ecommerce.user_service.dto.requests;

import jakarta.validation.constraints.*;

public final class PasswordRequests {
    private PasswordRequests() {
    }


    public static class Change {
        @NotBlank
        String oldPassword;
        @NotBlank
        @Size(min = 8, max = 100)
        String newPassword;

        public String getOldPassword() {
            return this.oldPassword;
        }

        public String getNewPassword() {
            return this.newPassword;
        }

        public void setOldPassword(final String oldPassword) {
            this.oldPassword = oldPassword;
        }

        public void setNewPassword(final String newPassword) {
            this.newPassword = newPassword;
        }
    }


    public static class Forgot {
        @NotBlank
        @Email
        String email;

        public String getEmail() {
            return this.email;
        }

        public void setEmail(final String email) {
            this.email = email;
        }
    }


    public static class Reset {
        @NotBlank
        @Email
        String email;
        @NotBlank
        String otp;
        @NotBlank
        @Size(min = 8, max = 100)
        String newPassword;

        public String getEmail() {
            return this.email;
        }

        public String getOtp() {
            return this.otp;
        }

        public String getNewPassword() {
            return this.newPassword;
        }

        public void setEmail(final String email) {
            this.email = email;
        }

        public void setOtp(final String otp) {
            this.otp = otp;
        }

        public void setNewPassword(final String newPassword) {
            this.newPassword = newPassword;
        }
    }


    public static class Verify {
        @NotBlank
        @Email
        String email;
        @NotBlank
        String otp;

        public String getEmail() {
            return this.email;
        }

        public String getOtp() {
            return this.otp;
        }

        public void setEmail(final String email) {
            this.email = email;
        }

        public void setOtp(final String otp) {
            this.otp = otp;
        }
    }
}
