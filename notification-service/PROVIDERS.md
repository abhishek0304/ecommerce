# SMTP and Twilio delivery

Copy missing keys from `providers.env.example` into the root `.env`. Keep real credentials only in that ignored file or your deployment secret store. `scripts/init-env.ps1` includes the template for new installations. Docker Compose forwards these settings to notification-service. Spring Boot launched from an IDE does not automatically load `.env`: configure the same environment variables in its run configuration. Kubernetes wiring is described below.

## Kubernetes provider configuration

The base and observability overlay now wire all 16 settings from `providers.env.example` to notification-service using individual keys from Secret `ecommerce-notification-providers`. Other services do not receive those provider credentials. References are optional so installations without the Secret retain disabled-channel defaults; enabling a channel still requires its complete provider configuration.

Create the deployment namespace first. Populate an ignored `.env.notification-production` file with only the provider template keys, using the selected environment's credentials and flags, or have the production secret manager materialize the same named Secret. Never copy the full application `.env` into this provider Secret.

```powershell
kubectl -n ecommerce create secret generic ecommerce-notification-providers --from-env-file=.env.notification-production
kubectl apply -k infra/k8s/base
kubectl -n ecommerce rollout restart deployment/notification-service
kubectl -n ecommerce rollout status deployment/notification-service --timeout=300s
```

For an existing Secret, update it through your secret manager or a secure administrator workflow; `create` deliberately fails rather than silently replacing it. Restart the deployment after changing Secret-backed environment variables. Both overlays use the same wiring. These commands require a configured cluster and do not establish actual provider delivery. SMTP usernames/passwords, enabled flags and non-secret SMTP settings all belong to this single configuration unit; default false flags are not forced over a supplied Secret.

## Test credentials versus actual receipt

[Twilio test credentials](https://www.twilio.com/docs/iam/test-credentials) validate simulated requests but do not send to real phones, create real message logs, or prove handset delivery. The sandbox diagnostic is `scripts/test-sms.ps1 -TestMode -Recipient +COUNTRYCODE...`; it uses **separate** `TWILIO_TEST_ACCOUNT_SID` and `TWILIO_TEST_AUTH_TOKEN` entries in the ignored root `.env` and a Twilio magic sender. It never falls back to live credentials and does not poll a simulated message SID. These diagnostic keys are not injected into the application deployment.

Actual SMS receipt requires explicitly using the normal configured account/sender and an authorized test recipient; a trial account can impose destination restrictions. SMTP has no universal sandbox switch: use a local capture server for simulation, or send an explicitly authorized test email to verify a real inbox. Provider acceptance and simulated success must not be reported as actual recipient delivery.

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
