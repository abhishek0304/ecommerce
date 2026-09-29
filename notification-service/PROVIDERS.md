# SMTP and Twilio delivery

Copy missing keys from `providers.env.example` into the root `.env`. Keep real credentials only in that ignored file or your deployment secret store. `scripts/init-env.ps1` includes the template for new installations. Docker Compose forwards these settings to notification-service. Spring Boot launched from an IDE does not automatically load `.env`: configure the same environment variables in its run configuration. Kubernetes deployments must supply them through their own Secret/environment configuration.

## Email

Set `EMAIL_FROM` to a sender allowed by your SMTP provider and fill `SMTP_HOST`, `SMTP_USERNAME`, and `SMTP_PASSWORD`. Use provider SMTP credentials (which may be an app password), not an assumed website login password.

- For authenticated STARTTLS, typically port 587: `SMTP_AUTH=true`, `SMTP_STARTTLS=true`, `SMTP_SSL=false`.
- For implicit TLS, typically port 465: `SMTP_AUTH=true`, `SMTP_STARTTLS=false`, `SMTP_SSL=true`.
- Follow your provider's exact host/port requirements. Then set `EMAIL_ENABLED=true`.
- For a local SMTP capture server, disable authentication/TLS as appropriate. From Docker Desktop use `host.docker.internal` to reach a server on Windows; `localhost` refers to the notification container itself.

## SMS and WhatsApp

Fill `TWILIO_ACCOUNT_SID` and `TWILIO_AUTH_TOKEN` from your Twilio account.

- SMS: set `TWILIO_SMS_FROM` to your SMS-capable Twilio sender in international format (for example `+1...`) and `SMS_ENABLED=true`.
- WhatsApp: set `TWILIO_WHATSAPP_FROM` to the registered WhatsApp sender number **without** the `whatsapp:` prefix. Set `TWILIO_WHATSAPP_CONTENT_SID` to an approved `HX...` content template accepting variable `1` as the order update text, then `WHATSAPP_ENABLED=true`. The transport adds the prefix itself. A sandbox requires enrolled recipients and a compatible template; an arbitrary SMS sender does not enable WhatsApp.
- Store the customer's phone number in international `+countrycode...` format. Enable the desired order notification preferences in the storefront. Account verification/reset codes are email-only.
- Twilio trial accounts require verified recipients. Check sender, destination, and template eligibility in your account before testing.

## Apply and verify

Once credentials are filled and enabled channels are intentional, rebuild/recreate the service from the repository root:

```powershell
docker compose --profile app up -d --build --no-deps notification-service
```

The databases, registry, Kafka, user-service, and gateway must already be running. Set `INTERNAL_SERVICE_KEY` consistently in user-service and notification-service, and ensure `USER_SERVICE_URL` reaches user-service. Enabling a channel also makes existing unexpired queued messages eligible for processing. Flags default to false; supplying credentials alone does not enable a channel.

1. Use your own test account and recipient details. Request an email verification or password reset code from the storefront. Confirm receipt in the inbox, including spam, and complete the verification/reset flow.
2. Enable email/SMS/WhatsApp preferences for the test account, then place one COD order. This creates order events and may produce more than one update per enabled channel. Verify messages on the intended phone and inbox.
3. Inspect the storefront message history or `GET /api/notifications/deliveries` using that customer's Bearer token. The worker checks every five seconds; a previously disabled channel may wait up to 60 seconds.
4. `ACCEPTED` means the SMTP server or Twilio accepted the request, **not confirmed recipient delivery**. Confirm Twilio's final status in its Messaging logs and receipt on the device. This application does not currently consume delivery-status callbacks. SMTP inbox receipt must be verified separately.

`PENDING` can mean a disabled channel or retry delay. `SKIPPED` means customer preferences/account status prevented sending. `FAILED` means rejection or exhausted retries. `EXPIRED` means the message deadline passed. `UNKNOWN` means a send may have succeeded before an error; inspect provider logs before resending to avoid duplicates. Terminal rows clear message bodies and recipients.

Order events create one queue row per channel, even when that channel is disabled. Their deadline is one day after enqueueing. The worker checks current customer preferences only when processing an enabled order channel; account-security emails bypass that lookup. `SENDING` records an in-progress attempt. A stale `SENDING` row becomes `UNKNOWN` after its 120-second processing window when next checked, rather than being sent again.

Recipient lookup failures and retryable transport rejections use delays of 60, 120, 240, and 480 seconds, with at most five attempts. Disabled-channel checks do not consume attempts. Twilio HTTP 429 is retryable; other 4xx responses fail. Timeouts, Twilio 5xx responses, and other uncertain send outcomes become `UNKNOWN` without automatic resend. SMTP authentication/parse failures fail immediately. Expiry is checked before another send. The worker processes up to 50 due rows per run; `messages.worker.enabled=false` disables scheduling and `messages.worker.delay-ms` changes the default 5000 ms delay.

Run local automated checks without sending real messages:

```powershell
.\mvnw.cmd -pl notification-service test
docker compose --profile app config --quiet
```

These tests verify application transport/queue behavior against mocks and local servers; they do not validate live credentials or prove inbox/handset delivery. Avoid sharing full rendered Compose configuration because it contains resolved secrets.

References: [Spring Boot mail configuration](https://docs.spring.io/spring-boot/reference/io/email.html), [Twilio message creation and statuses](https://www.twilio.com/docs/messaging/api/message-resource).
