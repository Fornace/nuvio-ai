# Lane 07 , Android BYOK Security and TV UX

**Research date:** 2026-08-29  
**Freshness order:** official Android material updated in the exact window **2026-07-31 through 2026-08-29** is presented first. Material updated before 2026-07-31 is explicitly labeled **Historical background**.

Nuvio should not add AI-provider BYOK as another string in Preferences DataStore, an addon URL, or plugin settings. The current code already demonstrates a usable Keystore/AES-GCM pattern for Simkl, but debrid, MDBList, Trakt, addon configuration, synced provider credentials, LAN configuration, and plugin runtimes expose several paths by which a future AI key could reach plaintext storage, URLs, a custom sync operator, logs, Sentry exception text, or untrusted same-process code. The recommended design is an app-owned credential broker backed by one versioned Android Keystore master alias, opaque scoped grants, no secret-bearing URLs, local-only keys by default, and an HTTPS QR/device-code flow that end-to-end encrypts raw key entry from the phone to the TV.

## Key Findings

### 1. Fresh official Android changes make LAN permission handling, Keystore alias discipline, password fields, network policy, and TV restore boundaries immediate design inputs

**What.** The official Android changes inside the required freshness window are:

1. Android 17 requires `ACCESS_LOCAL_NETWORK` at runtime for apps targeting API 37 that make or accept LAN connections; apps can instead use a system-mediated picker for supported discovery cases. Nuvio currently targets API 36, so this is not enforced yet, but its NanoHTTPD configuration flows will be affected when target SDK reaches 37 ([Android 17 target behavior changes, updated 2026-08-28](https://developer.android.com/about/versions/17/behavior-changes-17#local-network-protection-permission); `app/build.gradle.kts:98-107`).
2. Android 17 hides all password characters by default when a target-37 app receives password input from a physical keyboard, reducing shoulder-surfing exposure for a correctly typed password field ([Android 17 target behavior changes, updated 2026-08-28](https://developer.android.com/about/versions/17/behavior-changes-17#hide-pwd-kbd)).
3. Android 17 imposes per-app Keystore limits,50,000 keys for a non-system app targeting API 37+, 200,000 otherwise,and key creation beyond the limit throws `KeyStoreException` ([Android 17 all-app behavior changes, updated 2026-08-14](https://developer.android.com/about/versions/17/behavior-changes-all#per-app-keystore-limits)).
4. The network-security guide now documents Certificate Transparency enabled by default from Android 17, cleartext disabled by platform default from Android 9 unless an app opts in, and an Android 17 localhost exception; the page also says app-wide cleartext opt-in should be avoided where possible ([Network Security Configuration, updated 2026-08-28](https://developer.android.com/privacy-and-security/security-config)).
5. Current stable DataStore is 1.2.1; the page is current as of 2026-08-27. Encryption via `androidx.datastore:datastore-tink` and `AeadSerializer` exists only on the 1.3 alpha line as of this date, so it is useful evidence of Android's direction but is not a stable-production dependency recommendation yet ([DataStore guide, updated 2026-08-27](https://developer.android.com/topic/libraries/architecture/datastore); [DataStore releases, updated 2026-08-27](https://developer.android.com/jetpack/androidx/releases/datastore#1.3.0-alpha07)).
6. Credential Manager Restore Credentials can restore one app account through cloud backup or device-to-device transfer, but the current official page says it works on mobile and does **not** work across form factors. It cannot be assumed to move a credential from phone to Android TV ([Restore Credentials, updated 2026-08-27](https://developer.android.com/identity/sign-in/restore-credentials)).
7. Sign in with Google is cross-platform and requires explicit consent to share profile information, but it authenticates a Nuvio account; it is not a storage mechanism for third-party AI API keys ([Sign in with Google, updated 2026-08-14](https://developer.android.com/identity/sign-in/credential-manager-siwg)).

**Evidence.** Nuvio declares only `INTERNET` and Wi-Fi state access, targets API 36, starts LAN servers on device IPv4 addresses, and advertises plain `http://<ip>:<port>` QR URLs (`app/src/main/AndroidManifest.xml:13-22`; `app/build.gradle.kts:98-107`; `app/src/main/java/com/nuvio/tv/ui/screens/addon/AddonManagerViewModel.kt:250-259,361-375`). Android's fresh API-37 behavior therefore maps directly to an existing feature rather than a hypothetical future one.

**So what.** Design the runtime-permission state and denial UX before the target-37 upgrade; do not create one Keystore alias per plugin grant; mark raw-key fields as password inputs; and do not use Restore Credentials as the phone-to-TV BYOK transport. The preferred phone-assisted flow must be Nuvio's own short-lived HTTPS linking protocol or the provider's OAuth device-authorization protocol.

### 2. Current local credential storage is inconsistent: Simkl is encrypted, while debrid, MDBList, Trakt, custom-server configuration, and plugin settings are plaintext

**What.** Nuvio has one strong reusable pattern and several plaintext patterns:

- **Good baseline:** Simkl stores a profile-suffixed ciphertext in private SharedPreferences, encrypts with AES/GCM/NoPadding, and keeps the AES key under the Android Keystore alias `com.nuvio.tv.simkl.credentials.v1` (`app/src/main/java/com/nuvio/tv/data/simkl/AndroidSimklAuthStorage.kt:29-40,79-87,203-215,230-260,277-284`). It also invalidates stale async operations by binding them to profile plus generation and clears profile-specific ciphertext on profile removal (`AndroidSimklAuthStorage.kt:116-169,269-275`).
- **Plaintext:** Torbox, Premiumize, and Real-Debrid keys are `stringPreferencesKey` values read directly into `DebridSettings`; MDBList follows the same pattern (`app/src/main/java/com/nuvio/tv/data/local/DebridSettingsDataStore.kt:42-50,62-89,133-160`; `app/src/main/java/com/nuvio/tv/data/local/MDBListSettingsDataStore.kt:23-42,58-60`).
- **Plaintext:** Trakt access token, refresh token, device code, and user code are stored in profile-scoped Preferences DataStore (`app/src/main/java/com/nuvio/tv/data/local/TraktAuthDataStore.kt:29-44,49-73,75-126,145-185`).
- **Plaintext and potentially secret-bearing:** plugin `scraper_settings` is arbitrary JSON in Preferences DataStore; plugin repositories and downloaded JavaScript are also stored in app-private files without an integrity/signature requirement shown at the storage boundary (`app/src/main/java/com/nuvio/tv/data/local/PluginDataStore.kt:52-71,192-220,223-256`). CloudStream-compatible extension settings use a process-wide private SharedPreferences file and serialize arbitrary values as JSON (`app/src/full/java/com/lagradost/cloudstream3/utils/DataStore.kt:13-25,39-45,80-92`).
- **Configuration, not a user secret but still trust-sensitive:** a custom backend URL and publishable key are stored in plaintext SharedPreferences; discovery accepts both HTTP and HTTPS backends (`app/src/main/java/com/nuvio/tv/data/local/ServerConfigurationStore.kt:15-38,41-58`; `app/src/main/java/com/nuvio/tv/data/remote/ServerDiscovery.kt:60-79,90-110,119-133`).

**Evidence.** Preferences DataStore provides asynchronous, consistent, transactional persistence; its stable guide does not claim transparent encryption ([DataStore guide, updated 2026-08-27](https://developer.android.com/topic/libraries/architecture/datastore)). **Historical background:** Android Keystore keeps cryptographic key material in a container intended to make extraction more difficult and can keep key material outside the app process ([Android Keystore system, updated 2026-03-06](https://developer.android.com/privacy-and-security/keystore)).

**So what.** DataStore is not itself a secret store. Implement one broker storage backend and migrate all user-held long-lived credentials,future AI keys first, then debrid and tracking tokens,rather than cloning provider-specific storage classes. Keep non-secret provider preferences in DataStore, but replace the secret value with an opaque credential ID.

### 3. Current provider sync sends raw credential values to Supabase and custom server operators; this is not end-to-end encrypted sync

**What.** `ProviderCredentialSyncService` constructs JSON objects containing raw `api_key` or `client_id` values, sends them to `sync_push_provider_credentials`/`sync_seed_provider_credentials`, and pulls them back into local plaintext stores (`app/src/main/java/com/nuvio/tv/core/sync/ProviderCredentialSyncService.kt:38-58,83-130,150-188,191-243`; `app/src/main/java/com/nuvio/tv/core/sync/ProviderCredentialModels.kt:16-23,34-45`). The client-side evidence contains no wrapping key, ciphertext field, nonce, or authenticated associated data; TLS may protect transport when an HTTPS backend is used, but the RPC endpoint necessarily receives the raw JSON.

**Evidence.** Supabase is initialized from the active server's URL and key, automatically persists its own login session, and provider sync is available to that client (`app/src/main/java/com/nuvio/tv/core/di/SupabaseModule.kt:48-56,100-108`). Since custom-server discovery allows HTTP and the manifest opts into global cleartext, a configured HTTP custom backend can also remove transport confidentiality (`app/src/main/java/com/nuvio/tv/data/remote/ServerDiscovery.kt:60-79,119-133`; `app/src/main/AndroidManifest.xml:34-43`; `app/src/main/res/xml/network_security_config.xml:1-9`).

**So what.** For BYOK v1, make credentials **local to each device by default** and sync only metadata such as provider, masked suffix, status, and grant names. If cross-device credential sync is later required, it needs an explicit opt-in and client-side envelope encryption whose decryption key is unavailable to Supabase and to a custom-server operator; simply renaming `credential_json` to `encrypted` without client-held keys is not E2EE.

### 4. Secret-bearing addon and repository URLs are a cross-system leak multiplier

**What.** Nuvio intentionally preserves addon URL query strings because configurable Stremio addons commonly encode configuration in the URL. Those complete URLs are then persisted, fetched, cached, displayed, synced, exposed over LAN, and written to logs (`app/src/main/java/com/nuvio/tv/data/local/AddonPreferences.kt:46-75,98-110`; `app/src/main/java/com/nuvio/tv/data/repository/AddonRepositoryImpl.kt:59-72,220-236`; `app/src/main/java/com/nuvio/tv/core/sync/AddonSyncService.kt:53-79`). Plugin repository URLs are likewise synced as complete strings (`app/src/main/java/com/nuvio/tv/core/sync/PluginSyncService.kt:54-75`).

**Evidence.** The LAN page state serializes each addon's full `baseUrl`, and unauthenticated GET routes return page state and addon lists (`app/src/main/java/com/nuvio/tv/ui/screens/addon/AddonManagerViewModel.kt:338-351`; `app/src/main/java/com/nuvio/tv/core/server/AddonConfigServer.kt:34-50,76-91`). Repository, manifest, catalog, metadata, and stream paths are logged as full URLs (`app/src/main/java/com/nuvio/tv/data/repository/AddonRepositoryImpl.kt:220-236`; `app/src/main/java/com/nuvio/tv/data/repository/CatalogRepositoryImpl.kt:42-46,78-82`; `app/src/main/java/com/nuvio/tv/data/repository/StreamRepositoryImpl.kt:568-600`; `app/src/main/java/com/nuvio/tv/data/repository/MetaRepositoryImpl.kt:475-500`).

**So what.** A BYOK key must never be placed in URL userinfo, path, query, fragment, addon manifest URL, repository URL, stream URL, redirect URL, or QR URL. Existing addon URLs should be classified as potentially secret-bearing legacy data and immediately subjected to centralized display/log/LAN redaction; new AI addons should receive only an opaque grant handle or host-mediated result, never the key.

### 5. The current LAN configuration server is unsuitable for raw-key entry

**What.** `AddonConfigServer` starts NanoHTTPD on an available port, has no bearer token, cookie session, origin check, client pairing, CSRF protection, or per-request authorization in its route dispatcher, and returns full state to any LAN caller. Writes are deferred for TV confirmation, which limits silent mutation, but reads are immediate and an attacker can still observe configuration or generate repeated pending changes (`app/src/main/java/com/nuvio/tv/core/server/AddonConfigServer.kt:10-50,76-91,160-209,221-250`). The QR contains only a plain HTTP IP/port URL (`app/src/main/java/com/nuvio/tv/ui/screens/addon/AddonManagerViewModel.kt:361-375`). Debrid formatter LAN updates are applied immediately and likewise show no pairing token (`app/src/main/java/com/nuvio/tv/core/server/DebridFormatterConfigServer.kt:19-30,55-96,123-142`).

**Evidence.** Android's fresh LAN documentation states that both outgoing and accepted incoming TCP traffic are covered, enforcement applies beneath OkHttp and other networking APIs, and target-37 apps must request `ACCESS_LOCAL_NETWORK` or use a supported system picker ([Local network permission, updated 2026-07-13,**Historical background**, but incorporated by the fresh Android 17 behavior page](https://developer.android.com/privacy-and-security/local-network-permission); [Android 17 target behavior, updated 2026-08-28](https://developer.android.com/about/versions/17/behavior-changes-17#local-network-protection-permission)).

**So what.** Do not add `/api/credentials` to these servers. Use provider device authorization or a cloud-mediated HTTPS linking flow. If an offline LAN fallback is required, the browser must encrypt the key to an ephemeral public key embedded in the TV-displayed QR, use a one-time bearer carried in the URL fragment rather than the HTTP request URL, require a TV confirmation phrase, expire within minutes, and expose no general app state.

### 6. Logging and Sentry defenses are partial; several current paths can disclose tokens or secret-bearing URLs

**What.** Positive controls exist: release OkHttp logging is `NONE`, debug is only `BASIC`; Sentry disables default PII, screenshots, view hierarchy, request traces, and automatic network breadcrumbs; its custom network breadcrumb strips query and fragment (`app/src/main/java/com/nuvio/tv/core/di/NetworkModule.kt:127-142,161-164`; `app/src/main/java/com/nuvio/tv/core/diagnostics/SentryInitializer.kt:54-76`; `app/src/main/java/com/nuvio/tv/core/diagnostics/SentryNetworkBreadcrumbInterceptor.kt:82-88`). Auth diagnostic JSON/header values with credential-like field names are redacted (`app/src/main/java/com/nuvio/tv/core/auth/diagnostics/AuthDiagnostics.kt:45-48,65-99,402-441`).

However, these controls do not form a complete non-leakage contract:

- `rawForLog`, `urlForLog`, and the default `bodySnippetForLog` return the original value, and `diagnosticSummary` includes raw exception messages (`app/src/main/java/com/nuvio/tv/core/logging/LogDiagnostics.kt:3-25`). Account QR logs therefore print raw nonce, device code, user code, and full verification URL (`app/src/main/java/com/nuvio/tv/ui/screens/account/AccountViewModel.kt:290-304,320-343`).
- Sentry `beforeSend` clears only `event.request` and `event.user`; it does not sanitize exception values, messages, breadcrumb `error_message`, tags, or arbitrary extras (`app/src/main/java/com/nuvio/tv/core/diagnostics/SentryInitializer.kt:73-76,90-108`). The custom breadcrumb records raw encoded paths and up to 240 characters of an exception message (`app/src/main/java/com/nuvio/tv/core/diagnostics/SentryNetworkBreadcrumbInterceptor.kt:48-69`).
- The UI promises that passwords, tokens, request/response bodies, headers, cookies, raw diagnostics, and stream URL query/fragment values are not sent, but the implementation cannot guarantee this for exception strings or paths (`app/src/main/res/values/strings.xml:541-544`; `app/src/main/java/com/nuvio/tv/core/diagnostics/SentryInitializer.kt:73-76,98-107`).
- The JavaScript runtime logs request URL and request body, response URL and a 300-character response preview, and plugin-provided console output (`app/src/full/java/com/nuvio/tv/core/plugin/PluginRuntime.kt:280-300,506-539,582-600`).

**Evidence.** **Historical background:** Android warns that Logcat disclosure is a privacy risk and recommends avoiding sensitive logging even though normal apps have restricted Logcat access on modern Android ([Log Info Disclosure, updated 2024-09-24](https://developer.android.com/privacy-and-security/risks/log-info-disclosure)).

**So what.** Make redaction structural, not regex-only and not caller-optional. A secret should be represented by a non-string `CredentialHandle`; network code should never construct a secret-bearing URL; and every log/Sentry sink should accept only a sanitized `SafeUrl`/`SafeError` representation. Until that lands, Sentry's user-facing “Not sent” copy overstates the guarantee.

### 7. Keystore encryption alone does not protect BYOK from untrusted same-process plugin code

**What.** Nuvio supports two materially different plugin trust levels:

- JavaScript scrapers run in QuickJS, receive their entire arbitrary settings map and the app's compiled TMDB API key, and get a bridge that can make arbitrary HTTP requests with caller-provided headers and bodies (`app/src/full/java/com/nuvio/tv/core/plugin/PluginRuntime.kt:286-305,448-452,506-558,676-714`). This is containable only if secrets never enter `SCRAPER_SETTINGS`, plugin console data, or the JavaScript heap.
- External DEX extensions load into the app process and are passed app/activity context. CloudStream compatibility exposes app-private SharedPreferences APIs, and its shared client explicitly ignores all SSL errors (`app/src/full/java/com/lagradost/cloudstream3/utils/DataStore.kt:13-33,39-92`; `app/src/full/java/com/nuvio/tv/core/runtime/PluginRuntimeHooks.kt:40-62`). A same-UID, same-process DEX can read files/preferences and invoke or reflect into app code; an in-process broker API cannot establish a meaningful confidentiality boundary against it.

**Evidence.** The unqualified main app client installs a trust-all `X509TrustManager` and hostname verifier (`app/src/main/java/com/nuvio/tv/core/di/NetworkModule.kt:103-116`), and the Trakt client derives from that base. Direct debrid, custom-server auth and Simkl use separately constructed clients and must be reviewed independently (`NetworkModule.kt:127-143,169-184`). This remains a critical transport issue: every credential-bearing consumer of the unqualified client is interceptable by an on-path attacker despite an `https://` URL. The implementation phase must enumerate Retrofit/API bindings against each qualified and unqualified client before changing them.

**So what.** The broker can safely serve first-party code and restricted QuickJS operations, but **must deny BYOK grants to same-process DEX plugins**. Supporting DEX BYOK requires moving extension execution into an `isolatedProcess` service with no app-data access and mediating both network and credential operations over a narrow Binder contract; otherwise the only honest policy is “external extensions cannot use app-managed credentials.” Remove all trust-all TLS behavior before any BYOK launch.

### 8. The recommended architecture is app broker + Keystore envelope encryption + opaque scoped grants; direct plugin storage is unacceptable

**What.** The alternatives compare as follows:

| Option | At-rest extraction resistance | Prevents key in URL/log | Can revoke per consumer | Protects from JS | Protects from same-process DEX | Verdict |
|---|---:|---:|---:|---:|---:|---|
| Direct plugin/addon storage | No; current stores are plaintext | No | Weak | No | No | **Reject** |
| Keystore-encrypted value exposed through `getSecret()` | Yes | No; callers can stringify it | Partial | No if exposed | No | Storage primitive only |
| One Keystore alias per plugin/key/grant | Yes | Depends on API | Yes | Depends | No | Avoid alias proliferation; Android 17 now has explicit limits |
| App credential broker with one master alias per install/key version, opaque credential IDs, operation/host grants, and no secret-return API | Yes | Yes by construction | Yes | Yes, if JS only receives broker results | No | **Recommended for first-party + JS** |
| Broker plus isolated-process DEX runtime | Yes | Yes | Yes | Yes | Yes, subject to IPC/runtime hardening | Long-term DEX option |

**Evidence.** Simkl proves Nuvio can use Android Keystore with AES-GCM and a stable alias (`AndroidSimklAuthStorage.kt:230-260,277-284`). Android 17's fresh key limits reinforce the design choice to use a small number of master aliases rather than aliases per grant ([Android 17 all-app behavior changes, updated 2026-08-14](https://developer.android.com/about/versions/17/behavior-changes-all#per-app-keystore-limits)).

**So what.** The broker API must offer operations such as `test`, `executeProviderRequest`, `revoke`, and `delete`, not `readSecret`. A grant should constrain profile/device scope, provider, operations, destination host templates, data categories, expiry, budget, and consumer identity; the grant ID is not a Keystore alias and contains no key material.

### 9. Nuvio already has the right TV interaction primitive,QR/device code,but raw-key entry needs stricter consent, scope, cost, reveal, and revocation semantics

**What.** Existing Torbox and Premiumize flows start provider device authorization, display a provider verification URL and user code as a QR, poll, and save the resulting token (`app/src/main/java/com/nuvio/tv/ui/screens/settings/DebridSettingsViewModel.kt:251-309,315-365`; `app/src/main/java/com/nuvio/tv/ui/screens/settings/DebridSettingsScreen.kt:1194-1283,1317-1415`). This is preferable to entering a long key with a remote. Existing manual-key validation also tests Torbox/Premiumize before saving (`DebridSettingsViewModel.kt:210-249`).

**Evidence.** **Historical background:** Android TV's Gboard supports password input types, but TV has a constrained input layout and voice entry; password fields should use the password input type and must not allow voice dictation for secrets ([On-screen keyboard, updated 2026-03-05](https://developer.android.com/training/tv/get-started/onscreen-keyboard)). TV navigation is D-pad/focus based, should be predictable, and should provide a visible Cancel action when the only alternatives are confirm/destructive actions ([Navigation on TV, updated 2025-05-09,Historical background](https://developer.android.com/design/ui/tv/guides/foundations/navigation-on-tv); [Focus system, updated 2024-03-21,Historical background](https://developer.android.com/design/ui/tv/guides/styles/focus-system)).

**So what.** Reuse device authorization whenever the provider supports it. For raw API keys, make phone/desktop HTTPS link entry primary and TV password input only a fallback. The final TV confirmation must show provider, verified account/plan if available, profile versus device scope, model/language, data sent, estimated provider cost, budget controls, consumer grants, and whether encrypted sync is enabled before saving.

## Data / Evidence

### Current Nuvio credential and configuration inventory

| Data | Current scope | At rest | Sync / export | Principal leak paths | Assessment |
|---|---|---|---|---|---|
| Debrid API keys | Active app profile | Plain Preferences DataStore (`DebridSettingsDataStore.kt:42-50,62-89`) | Raw `credential_json` through provider RPC (`ProviderCredentialSyncService.kt:178-188,191-224`) | DataStore + `.bak`, custom sync operator, error strings | **High risk** |
| MDBList API key | Active app profile | Plain Preferences DataStore (`MDBListSettingsDataStore.kt:23-42`) | Same provider RPC (`ProviderCredentialSyncService.kt:209-215`) | DataStore + `.bak`, custom sync operator | **High risk** |
| AnimeSkip client ID | Active app profile | Plain Preferences DataStore | Same provider RPC (`ProviderCredentialSyncService.kt:216-220`) | Client ID may be non-secret, but current model does not distinguish secret/public | **Needs classification** |
| Trakt access/refresh tokens | Active app profile | Plain Preferences DataStore (`TraktAuthDataStore.kt:57-89,120-126`) | Not in `ProviderCredentialSyncService`; provider account itself is remote | DataStore + `.bak`; logs/error paths | **High risk** |
| Simkl token | Active app profile | AES-GCM ciphertext in private SharedPreferences; Keystore master alias (`AndroidSimklAuthStorage.kt:203-260,277-284`) | No raw provider-credential sync shown | Same-process code can request/read decrypted token; no AAD in current format | **Best current baseline, not full broker** |
| Supabase/Nuvio session | App account | SDK `autoLoadFromStorage`/`autoSaveToStorage` (`SupabaseModule.kt:100-104`) | Backend session | Exact SDK storage format/encryption not established from repository source | **Open** |
| Addon configuration URLs | Primary or active profile according to `usesPrimaryAddons` | Plain DataStore; full query retained (`AddonPreferences.kt:38-75`) | Full URL to Supabase (`AddonSyncService.kt:53-79`) | Logs, LAN GET state, cache, custom server, addon origin | **Treat as potentially secret-bearing** |
| Plugin repository URLs/settings/code | Primary or active profile according to `usesPrimaryPlugins` | Plain DataStore/files (`PluginDataStore.kt:36-71,192-256`) | Repository URL, not settings, is synced (`PluginSyncService.kt:54-75`) | Runtime injection, logs, arbitrary fetch, same-process DEX | **Never store BYOK here** |
| Custom server config | Device | Plain SharedPreferences (`ServerConfigurationStore.kt:15-51`) | Selected locally | Allows HTTP backend; custom operator receives account/provider data | **Trust-sensitive** |
| App-level build credentials | App binary, not user profile | Compiled into `BuildConfig` (`app/build.gradle.kts:110-123`) | Installed APK | Extractable from app; TMDB key is injected into every JS runtime (`PluginRuntime.kt:448-452,676-683`) | **Do not confuse with protectable user BYOK** |

### Backups and deletion

- Android Auto Backup is disabled with `android:allowBackup="false"` (`app/src/main/AndroidManifest.xml:34-43`). That is appropriate for current plaintext credentials, but it also means Android Restore Credentials is not a substitute for app data migration and, in any case, current Restore Credentials is mobile-only ([Restore Credentials, updated 2026-08-27](https://developer.android.com/identity/sign-in/restore-credentials)).
- Nuvio creates its own unencrypted corruption-recovery copy beside each Preferences DataStore (`*.preferences_pb.bak`), including stores that contain debrid and Trakt secrets (`app/src/main/java/com/nuvio/tv/data/local/ProfileDataStoreFactory.kt:135-190,196-219`). This doubles plaintext-at-rest copies.
- Profile deletion searches for files ending in `_p<id>.preferences_pb`, not `.preferences_pb.bak`; global profile clearing classifies only names ending exactly in `.preferences_pb`. Uncached stale `.bak` files can therefore survive deletion/sign-out cleanup (`ProfileDataStoreFactory.kt:105-132`; `app/src/main/java/com/nuvio/tv/core/profile/ProfileManager.kt:105-139`).
- Sign-out clears active profile stores and known credential stores, but only Simkl is registered through `ProfileScopedCredentialStore` in the inspected bindings; plugin code directories are also deleted (`app/src/main/java/com/nuvio/tv/core/auth/AccountLocalDataResetService.kt:20-55`; `app/src/main/java/com/nuvio/tv/core/profile/ProfileScopedCredentialStore.kt:1-6`; `app/src/main/java/com/nuvio/tv/core/di/TrackingModule.kt:38-40`).

**Historical background:** Android recommends excluding particularly sensitive data from backups or requiring end-to-end encryption, and notes that standard Auto Backup can be configured with exclusion rules ([Security recommendations for backups, updated 2024-10-25](https://developer.android.com/privacy-and-security/risks/backup-best-practices)). For Nuvio, the more important current defect is its own plaintext `.bak` copy, not cloud Auto Backup.

### Threat model

| Threat actor / event | Asset and entry point | Current exposure | Required control |
|---|---|---|---|
| Malicious device on same Wi-Fi/Ethernet | Addon URLs, collection state, configuration changes via NanoHTTPD | Unauthenticated HTTP GET state and change proposals (`AddonConfigServer.kt:34-50,76-91,160-209`) | Runtime LAN permission only on explicit launch; one-time authenticated session; no secret endpoint; no general state in BYOK flow |
| Passive/active LAN observer | Raw key submitted from phone to TV | Existing LAN QR is HTTP (`AddonManagerViewModel.kt:367-375`) | Provider device OAuth or HTTPS relay + browser-to-TV E2EE; no raw key over LAN plaintext |
| Malicious addon/provider origin | Key embedded in addon URL or injected into addon request | URL query is retained and sent to origin (`AddonPreferences.kt:53-75`; `AddonRepositoryImpl.kt:220-227`) | Reject credential-shaped URL components; broker owns provider call; addon gets opaque result/grant |
| Malicious QuickJS scraper | AI/debrid key in `SCRAPER_SETTINGS`; arbitrary fetch | Settings and app TMDB key injected; unrestricted fetch bridge (`PluginRuntime.kt:448-452,506-558,676-714`) | Never inject user secrets; broker operation returns sanitized data; host allowlist and data budget |
| Malicious external DEX | All app-private files, broker code, network | Same process/context + compatibility prefs; SSL errors ignored (`DataStore.kt:13-33,80-92`; `PluginRuntimeHooks.kt:51-62`) | Deny BYOK or move to isolated process with mediated IPC/network |
| Custom sync server/operator or compromised backend | Raw provider credential RPC | Raw `credential_json` supplied to RPC (`ProviderCredentialSyncService.kt:178-188`) | Local-only default; opt-in client-side envelope encryption; custom-server trust warning and separate consent |
| Log/Sentry recipient, bug-report collector, ADB user | Keys in URLs, bodies, error messages, QR codes | Full URL/body logs; Sentry does not scrub all event fields (`LogDiagnostics.kt:3-25`; `SentryInitializer.kt:73-76`; `PluginRuntime.kt:506-600`) | Typed safe logging, sink-level redaction, Sentry event scrub, release leak tests |
| Lost/shared living-room TV, shoulder surfer, child profile | Full key/reveal UI; device-wide grants | Current profile IDs separate most stores, but primary addons/plugins can be shared (`AddonPreferences.kt:38-44`; `PluginDataStore.kt:44-50`) | Mask by default; no full reveal on TV; profile PIN/adult confirmation; grants independent of shared addon/plugin flags |
| File extraction/root/forensic backup | Plain DataStore and `.bak` files | Debrid/Trakt/MDBList plaintext plus shadow copies (`ProfileDataStoreFactory.kt:184-219`) | Keystore envelope encryption; no plaintext shadow copy; deletion tests; zeroize best-effort memory |
| Replay or link-session hijack | QR session, manual code, encrypted payload | Existing QR logs raw codes/nonces (`AccountViewModel.kt:290-304,341-343`) | 5-minute expiry, one redemption, high-entropy code, rate limit, TV confirmation phrase, no raw logging |
| Provider key overuse or unexpected billing | AI request grant | No AI BYOK policy exists | Operation/model allowlist, per-profile budget, cost preview, provider quota test, audit metadata without prompts or keys |

### Required non-leakage contract

The following are release-blocking invariants, not best-effort guidance:

1. **URL invariant:** no credential value in URI userinfo, host, port, path, query, fragment, redirect, QR payload, addon base URL, repository URL, media URL, or exception message. Reject inputs whose normalized query/path contains configured secret values or sensitive names such as `key`, `token`, `secret`, `authorization`, `bearer`, and provider-specific aliases. Legacy addon URLs are sanitized to `scheme://host/<redacted-path>` for telemetry and to `scheme://host/path?…` for UI; never print raw queries (`AddonPreferences.kt:53-75`; [Network Security Configuration, updated 2026-08-28](https://developer.android.com/privacy-and-security/security-config)).
2. **Logging invariant:** production logs receive only route templates, method, status, duration, provider ID, credential ID hash, and error class. They never receive request/response bodies, headers, raw URLs, QR codes, account identifiers, prompts, media titles tied to a key, or exception messages. Replace the current no-op safe-log functions (`LogDiagnostics.kt:3-25`).
3. **Sentry invariant:** `beforeSend` recursively removes or hashes all URL paths not on an allowlist; removes query/fragment, request/response data, headers, cookies, exception messages that match secret fingerprints, breadcrumbs' `error_message`, extras, contexts, spans, and serialized plugin diagnostics. Send only error class and a generated safe reason code. Current request/user clearing is insufficient (`SentryInitializer.kt:73-76`; `SentryNetworkBreadcrumbInterceptor.kt:48-69`).
4. **Sync invariant:** the default is no secret sync. Profile-sync metadata contains `credentialId`, provider, mask, state, and grant metadata only. Opt-in secret sync contains authenticated ciphertext and wrapped data keys only; device-scope credentials never sync. Current raw credential JSON must not be extended to AI keys (`ProviderCredentialSyncService.kt:178-224`).
5. **Backup invariant:** neither plaintext nor decrypted values enter Preferences DataStore, Room, SavedState, cache, crash attachment, screenshot, clipboard, external storage, `.bak`, or Android Auto Backup. Ciphertext may be locally recoverable only if the Keystore alias remains; otherwise fail closed and request relinking.
6. **LAN invariant:** no LAN endpoint returns credential metadata or accepts a raw secret. A dedicated optional import endpoint accepts only a one-time authenticated E2EE ciphertext envelope and cannot read addons, profiles, collections, or provider status. Stop the server on background, screen exit, timeout, network change, permission revocation, or successful import.
7. **Plugin invariant:** JavaScript receives `GrantHandle`, never a secret. DEX receives no grant while it is same-process. Plugin logs and diagnostic callbacks are untrusted input and are sanitized before reaching Logcat/Sentry (`PluginRuntime.kt:280-300,506-600`).
8. **Memory invariant:** decrypt as late as possible inside the broker operation, do not expose a Kotlin data-class `toString`, avoid retaining the key in StateFlow/Compose state, and overwrite mutable byte buffers best-effort after request construction. Java/Kotlin cannot guarantee erasure, so isolation and short lifetime remain the primary controls.

### Credential broker contract

```kotlin
@JvmInline value class CredentialId(val value: String)        // random, opaque
@JvmInline value class GrantHandle(val value: String)         // random, opaque, non-secret

enum class CredentialScopeType { PROFILE, DEVICE }
enum class ConsumerType { FIRST_PARTY, QUICKJS_PLUGIN, REMOTE_ADDON, EXTERNAL_DEX }
enum class CredentialOperation { TEST, INFER_TEXT, TRANSCRIBE, TRANSLATE, EMBED, REVOKE }

data class CredentialMetadata(
    val id: CredentialId,
    val providerId: String,
    val scopeType: CredentialScopeType,
    val scopeId: String,                 // profile ID or device installation ID
    val label: String,
    val maskedSuffix: String?,           // e.g. ••••A7K2; never a reversible prefix
    val state: State,                    // UNTESTED, VALID, INVALID, REVOKED
    val createdAt: Instant,
    val lastTestedAt: Instant?,
    val providerAccountLabel: String?,   // sanitized account/plan response
    val syncPolicy: SyncPolicy
)

data class GrantPolicy(
    val credentialId: CredentialId,
    val consumerType: ConsumerType,
    val consumerId: String,              // signed first-party module or plugin hash/repo ID
    val operations: Set<CredentialOperation>,
    val destinationHostTemplates: Set<String>,
    val allowedDataClasses: Set<DataClass>,
    val allowedLanguages: Set<String>,
    val allowedModels: Set<String>,
    val maxRequestCostMicros: Long?,
    val monthlyBudgetMicros: Long?,
    val expiresAt: Instant?,
    val requireForeground: Boolean,
    val requirePerUseConfirmation: Boolean
)

interface CredentialBroker {
    suspend fun importEncrypted(envelope: ImportEnvelope, consent: ConsentReceipt): CredentialMetadata
    suspend fun test(id: CredentialId): TestResult
    suspend fun grant(id: CredentialId, policy: GrantPolicy, consent: ConsentReceipt): GrantHandle
    suspend fun execute(handle: GrantHandle, request: ProviderOperationRequest): SanitizedProviderResult
    suspend fun revoke(id: CredentialId, revokeAtProvider: Boolean): RevokeResult
    suspend fun delete(id: CredentialId)
    fun observeMetadata(scope: Scope): Flow<List<CredentialMetadata>>
    // Deliberately absent: getSecret(), exportPlaintext(), appendToUrl().
}
```

Contract rules:

- `execute` resolves the credential and grant inside the broker, verifies active profile/device, foreground state, operation, model, language, destination, data category, expiry, and budget, constructs the provider request itself, and returns only the result needed by the consumer.
- `FIRST_PARTY` and restricted `QUICKJS_PLUGIN` are eligible. `REMOTE_ADDON` is eligible only for a host-owned operation whose response is safe to return. `EXTERNAL_DEX` returns `UNSUPPORTED_UNTRUSTED_RUNTIME` until process isolation exists (`PluginRuntime.kt:448-452`; `DataStore.kt:13-33`).
- `usesPrimaryAddons` and `usesPrimaryPlugins` must not imply credential sharing. Those flags currently redirect storage to profile 1 (`AddonPreferences.kt:38-44`; `PluginDataStore.kt:44-50`), but broker grants require separate adult consent.
- Every destructive action creates a tombstone so remote encrypted copies and all grants are deleted. Provider-side revocation is attempted where supported; local deletion still completes with an explicit “provider revocation could not be confirmed” state. Trakt already demonstrates provider revoke then local clear (`app/src/main/java/com/nuvio/tv/data/repository/TraktAuthService.kt:304-322`); debrid “Disconnect” currently only clears the saved value (`DebridSettingsScreen.kt:1372-1379`).

### Keystore record and alias design

Use a small alias set:

- `com.nuvio.tv.byok.master.v1` , device master AES-256-GCM key.
- Optional future `com.nuvio.tv.byok.sync-wrap.v1` , asymmetric device key used only to unwrap cross-device data keys.
- Do **not** create aliases per provider, profile, plugin, grant, or credential; Android 17 now explicitly limits per-app aliases ([Android 17 all-app behavior, updated 2026-08-14](https://developer.android.com/about/versions/17/behavior-changes-all#per-app-keystore-limits)).

Store each record as:

```text
formatVersion | credentialId | providerId | scopeType | scopeId |
createdAt | keyAliasVersion | 96-bit random IV | AES-GCM ciphertext+tag |
secretFingerprint(HMAC, truncated; redaction matching only)
```

Authenticated associated data must be immutable and unique to the record:

```text
"nuvio-byok-v1|<applicationId>|<installId>|<scopeType>|<scopeId>|<providerId>|<credentialId>"
```

This prevents swapping a ciphertext between profile, provider, installation, or credential records. Simkl's current AES-GCM implementation is a useful foundation but supplies no explicit AAD (`AndroidSimklAuthStorage.kt:230-260`); the broker format should add it. If the Keystore key is missing or invalidated, delete unusable ciphertext only after recording a safe recovery reason and ask the user to relink,never fall back to plaintext.

The current stable DataStore line does not provide stable transparent encrypted Preferences. The official `datastore-tink` `AeadSerializer` exists on 1.3 alpha and demonstrates unique associated data to prevent ciphertext swapping, but should be adopted only after a stable release or an explicit alpha risk decision ([DataStore releases, updated 2026-08-27](https://developer.android.com/jetpack/androidx/releases/datastore#1.3.0-alpha07)). Until then, a small broker-owned encrypted file/Room table with the Keystore primitive is safer than inserting ciphertext strings into every feature DataStore.

### TV QR/link/code UX

#### A. Provider device authorization , preferred

Use the provider's OAuth/device authorization when available, matching Nuvio's current Torbox/Premiumize and Trakt/Simkl patterns (`DebridSettingsViewModel.kt:251-309`; `DebridSettingsScreen.kt:1243-1283,1317-1415`).

1. TV shows provider name/logo, requested capability, profile, language/model defaults, data disclosure, cost statement, and **Connect**.
2. TV displays provider verification QR plus a large manual code and countdown; it polls at the provider's instructed interval.
3. Phone authenticates directly with the provider. Nuvio never handles the user's provider password and receives only the provider token.
4. TV tests a low-cost identity/quota endpoint, displays verified account/plan and scopes, then asks **Save for profile _X_** or **Save for this device**.
5. Back/Cancel aborts and clears the device code. Expiry presents a single focused **Generate new code** action.

#### B. Raw API key , HTTPS link with browser-to-TV E2EE

1. TV generates an ephemeral X25519/P-256 key pair in memory, 128+ bits of session entropy, a short human code, expiry (target five minutes), and a two-word confirmation phrase.
2. QR contains only an official `https://nuvio.tv/link-key/...` URL, session ID, ephemeral TV public key/fingerprint, and challenge. It contains no provider key and is never logged.
3. Phone page requires Nuvio account authentication when available, shows TV name, provider, target profile/device scope, exact operations, plugin/addon consumer, language/model, provider endpoint, data categories, retention link, estimated unit/monthly cost, and “charges go to your provider account.” Sign in with Google can authenticate the Nuvio account with explicit profile-sharing consent, but does not carry the API key ([Sign in with Google, updated 2026-08-14](https://developer.android.com/identity/sign-in/credential-manager-siwg)).
4. Key input is `type=password`, never voice-enabled, never placed in URL/localStorage/analytics, and is masked. The browser encrypts it with an AEAD key derived from ephemeral ECDH with the QR-pinned TV public key; the relay receives ciphertext only.
5. TV polls over authenticated HTTPS, decrypts in memory, verifies the session challenge and one-time nonce, tests the key directly against the provider, then shows safe identity/quota/plan results.
6. TV displays the two-word phrase also shown on the phone and requires **Confirm and save**. This prevents an attacker from silently pairing the phone page to another TV session.
7. Relay ciphertext, browser memory, TV ephemeral private key, and session are destroyed after success, cancellation, or expiry. Replay receives a terminal `already_redeemed` result.
8. Manual fallback uses an eight-or-more-character high-entropy code, rate limiting, and the same TV phrase; a six-digit code alone is too weak for a raw-key transfer.

#### C. On-TV fallback

- Use a password input type, disable voice input and suggestions, mask all characters, and show only length plus last four characters after entry. Android 17's physical-keyboard behavior helps only when the field is correctly marked as a password ([Android 17 target behavior, updated 2026-08-28](https://developer.android.com/about/versions/17/behavior-changes-17#hide-pwd-kbd)).
- Never put the key in `rememberSaveable`, SavedState, ViewModel `StateFlow`, clipboard, or accessibility text. Keep it in a short-lived mutable buffer owned by the dialog/controller.
- Provide **Test without saving**, **Clear**, and **Cancel**. The default focus is **Test**, not **Save**; Back cancels predictably.

#### D. Credential details, test, reveal, and revoke

Credential list rows show provider, label, `••••last4`, profile/device badge, state, last-tested date, enabled operations, consumer count, model/language, budget, and sync state,never key length or prefix.

- **Test:** uses the lowest-cost identity/models/quota endpoint available, sends no media/prompt content, and reports `Valid`, `Invalid/revoked`, `Network unavailable`, or `Provider rate-limited` without displaying raw response bodies.
- **Reveal:** default behavior is **no full reveal on the living-room TV**. Require profile PIN/adult re-authentication and send a new E2EE phone link that reveals for at most 30 seconds. If product insists on TV reveal, require hold-to-reveal, `FLAG_SECURE`, a 10-second timeout, no accessibility announcement of the value, and an explicit shoulder-surfing warning; plugins and addons can never invoke reveal.
- **Revoke:** show affected profile/device, plugins, addons, and operations. Revoke provider-side when supported, delete local ciphertext, invalidate grants and in-memory cache, emit a remote tombstone for opt-in encrypted sync, and verify subsequent provider use fails. **Disconnect** is not synonymous with provider revocation.
- **Rotate:** import/test replacement first, atomically switch grants, then revoke/delete old credential. Do not create a new Keystore alias per rotation.

### Profile versus device scope, provider/language/cost, and consent

**Default scope:** profile. Debrid, tracking, AI history, language, and spending are user-specific and Nuvio already stores most provider settings per active profile (`DebridSettingsDataStore.kt:42-43,62-63`; `TraktAuthDataStore.kt:72-76`). Device scope is an explicit household/admin choice.

| Decision | Profile scope | Device scope |
|---|---|---|
| Visibility | Only active profile metadata and grants | Metadata visible to adult/admin settings; child profiles still need grants |
| Sync | Off by default; optional E2EE profile sync | Never cloud-sync by default |
| Deletion | Profile deletion removes ciphertext, grants, tombstone, and backup remnants | App reset/device unlink removes it |
| Shared addons/plugins | No implicit access even if source configuration comes from primary profile | Each consumer still requires an explicit grant |
| Reveal/revoke | Profile PIN or account re-auth | Adult/admin confirmation; list all affected profiles |
| Budget | Per-profile provider/model budget | Household budget plus per-profile sublimits |

Consent receipt fields:

- provider and verified account/plan;
- profile or device scope;
- consumer name, publisher/repository, type, and immutable code hash/version;
- operation (text inference, transcription, translation, embedding, etc.);
- model and input/output language choices;
- destination hosts;
- data categories sent: title/metadata, subtitles, audio snippets, images, watch history, prompt text, device diagnostics;
- retention/training policy link and region when known;
- price basis, estimated per-action range, monthly hard/soft limit, and “billed by provider” statement;
- grant duration and foreground/per-use confirmation;
- sync policy and custom-server operator warning;
- timestamp and policy version, with no prompt, media payload, or secret in the receipt.

Consent must be renewed when provider host, data category, operation, model cost class, consumer code hash/publisher, or scope expands. Language/model changes within an already approved cost/data envelope can be normal settings changes.

### Redaction contract

Implement one `SecurityRedactor` used by Logcat, Sentry, auth diagnostics, plugin diagnostics, LAN display, and support exports:

```kotlin
interface SecurityRedactor {
    fun url(raw: String?): SafeUrl       // scheme + host + route template; no userinfo/query/fragment
    fun throwable(t: Throwable): SafeError // class + mapped reason; no raw message/cause text
    fun text(raw: String?): SafeText     // fingerprints + key-name patterns + JWT/bearer heuristics
    fun headers(raw: Headers): SafeHeaders // allowlist, not denylist
    fun json(raw: JsonElement): JsonElement // schema-aware sensitive field removal
}
```

Rules:

- Maintain HMAC fingerprints of active secrets solely to detect accidental inclusion without retaining plaintext. Match before truncation so a long token past character 240 is still removed.
- Header telemetry is allowlist-only (`Content-Type`, safe rate-limit counters, request ID); never record `Authorization`, cookies, API-key headers, or unknown headers.
- URL telemetry records a route template such as `https://api.provider.example/v1/models/{model}:invoke`, not `encodedPath`, because path segments can carry codes or configured addon secrets.
- Exceptions are mapped at source (`DNS`, `TLS`, `TIMEOUT`, `HTTP_401`) before arbitrary provider/plugin messages enter logs. Current `diagnosticSummary` walks six raw cause messages and must not be used in production (`LogDiagnostics.kt:16-25`).
- Plugin `console.*`, request body, response preview, and HTTP diagnostics are disabled in release and pass through the same redactor in debug (`PluginRuntime.kt:280-300,506-600`).
- Sentry `beforeSend` is a final recursive fail-safe, not the primary redactor; add a test event containing canary keys in every event field and inspect the server-side stored JSON (`SentryInitializer.kt:73-76`).

### Verification checklist

#### Static and build-time

- [ ] No `trustAllManager`, `hostnameVerifier { _, _ -> true }`, or `ignoreAllSSLErrors()` remains in any credential-capable process/client (`NetworkModule.kt:103-116`; `PluginRuntimeHooks.kt:51-62`).
- [ ] Release R8/lint rule fails on `Log.*` interpolation of names/types `token`, `key`, `secret`, `credential`, `authorization`, `url`, `body`, `headers`, `cookie`, `code`, or `nonce` unless the value is a `Safe*` type. Current ProGuard rules keep line data but have no log-removal/redaction rule (`app/proguard-rules.pro:1-154`).
- [ ] No broker API returns `String`/`ByteArray` plaintext to consumers; no `getSecret`, `export`, or URL injection method exists.
- [ ] Addon/repository validators reject userinfo and credential-shaped URL parameters; legacy URLs have safe-display/safe-log forms.
- [ ] `CredentialId` and `GrantHandle` have constant safe `toString()` output.
- [ ] Secrets do not appear in Compose state, navigation arguments, `rememberSaveable`, Parcelable/Serializable models, work requests, notifications, analytics, or support DTOs.

#### Crypto and storage unit tests

- [ ] AES-GCM round trip works; encrypting the same value twice produces different IV/ciphertext.
- [ ] Swapping ciphertext between provider, profile, device, credential ID, or installation fails AAD authentication.
- [ ] Alias missing/invalidated fails closed and never writes plaintext.
- [ ] Rotation retains old alias only until all records migrate, then deletes it; alias count stays constant per key version.
- [ ] Credential files and local corruption backups contain no plaintext canary under binary/string scan.
- [ ] Profile deletion and sign-out delete `.preferences_pb`, `.preferences_pb.bak`, broker records, grants, plugin code, and in-memory entries; current cleanup gaps around `.bak` are covered (`ProfileDataStoreFactory.kt:105-132`; `ProfileManager.kt:120-138`).

#### Redaction and telemetry tests

- [ ] Property-based tests generate JWTs, provider key formats, mixed-case key names, URL-encoded/base64 values, Unicode, multiline plugin errors, and keys split across exception causes.
- [ ] Canary secret is absent from Logcat, Sentry event JSON, breadcrumbs, ANR data, support reports, plugin diagnostics, OkHttp/Ktor logs, and crash exception values.
- [ ] URL tests cover userinfo, path, matrix params, query, fragment, redirects, `Location`, encoded delimiters, and addon configuration URLs.
- [ ] Sentry UI disclosure text is changed or implementation is strengthened until the “Not sent” statement is mechanically true (`strings.xml:541-544`).

#### Network and LAN instrumentation

- [ ] MITM proxy with an untrusted CA cannot intercept production provider/addon/sync TLS; hostname mismatch fails.
- [ ] No BYOK provider call uses HTTP. A custom HTTP sync server cannot receive or request secret sync.
- [ ] On Android 17/API 37 target, launching LAN config requests `ACCESS_LOCAL_NETWORK` only after an in-context rationale; deny/revoke closes the server and gives a recoverable UI ([Android 17 target behavior, updated 2026-08-28](https://developer.android.com/about/versions/17/behavior-changes-17#local-network-protection-permission)).
- [ ] An unauthenticated LAN client gets no state and no mutation endpoint; wrong bearer, Origin, CSRF token, session ID, or confirmation phrase fails.
- [ ] Server stops on app background, profile switch, screen exit, network interface change, timeout, success, and process lifecycle cleanup.
- [ ] Packet capture of offline fallback sees only E2EE ciphertext and non-sensitive session metadata; active payload substitution fails due to QR-pinned key/AAD.

#### QR/link security

- [ ] Session entropy is at least 128 bits; manual code is rate-limited and cannot reveal the session payload by enumeration.
- [ ] Expired, cancelled, already redeemed, wrong-provider, wrong-profile, wrong-device, replayed, and modified envelopes fail with safe reason codes.
- [ ] Relay/database/analytics access shows ciphertext only; browser page has no third-party scripts, pixels, session replay, or permissive CSP.
- [ ] QR/session/user code/nonce never appears in Logcat or Sentry; current account QR raw logging is removed (`AccountViewModel.kt:290-304,341-343`).
- [ ] TV and phone show the same confirmation phrase and provider/profile/scope before save.
- [ ] Process death before confirmation leaves no credential; after atomic confirmation leaves exactly one valid record.

#### Broker isolation and grants

- [ ] A malicious QuickJS fixture cannot access filesystem, preferences, Keystore, other plugin settings, raw key, authorization header, or unrestricted provider host; it can only invoke approved operations.
- [ ] Plugin console/request/response data containing a canary cannot reach logs (`PluginRuntime.kt:280-300,506-600`).
- [ ] Same-process DEX receives `UNSUPPORTED_UNTRUSTED_RUNTIME`; an isolated-process prototype cannot access app-private files and can call only authenticated Binder methods.
- [ ] Profile switch during an in-flight call cancels or rejects the response using a generation/scope check analogous to Simkl (`AndroidSimklAuthStorage.kt:55-87,116-169`).
- [ ] `usesPrimaryAddons`/`usesPrimaryPlugins` does not confer grants from profile 1 (`AddonPreferences.kt:38-44`; `PluginDataStore.kt:44-50`).
- [ ] Expired/revoked grants fail immediately; budget and destination checks are enforced inside the broker, not trusted to plugin UI.

#### UX, accessibility, and revocation

- [ ] All elements are reachable with D-pad, have obvious focused state, and Back/Cancel behaves predictably ([Navigation on TV,Historical background](https://developer.android.com/design/ui/tv/guides/foundations/navigation-on-tv)).
- [ ] Raw TV input uses password input type and disables voice/suggestions; TalkBack announces purpose/status but never the value ([On-screen keyboard,Historical background](https://developer.android.com/training/tv/get-started/onscreen-keyboard)).
- [ ] Test does not save; save requires provider/profile/scope/cost/data consent; reveal requires adult re-auth and auto-hides.
- [ ] Revoke invalidates local record, all grants, remote encrypted copy/tombstone, and provider token where supported; subsequent operation proves failure.
- [ ] Invalid/provider-revoked responses transition metadata to `INVALID` without placing provider response text in telemetry.

## Recommendations

1. **Block BYOK launch on the two critical prerequisites:** remove trust-all TLS from `NetworkModule` and CloudStream compatibility clients, and prohibit secret-bearing addon/repository URLs (`NetworkModule.kt:103-116`; `PluginRuntimeHooks.kt:51-62`; `AddonPreferences.kt:53-75`).
2. **Build the broker as a separate security module before provider UI.** Start with one Keystore AES-GCM master alias, AAD-bound encrypted records, metadata-only DataStore, profile-generation checks, operation-based API, and no plaintext getter. Reuse concepts, not code blindly, from Simkl (`AndroidSimklAuthStorage.kt:55-87,116-169,230-260`).
3. **Ship AI BYOK local-only in v1.** Do not add AI credentials to `ProviderCredentialSyncService`; sync only status/mask/grant metadata. This removes backend/custom-operator key custody while the E2EE recovery design remains unresolved (`ProviderCredentialSyncService.kt:178-224`).
4. **Migrate existing credentials in order:** debrid and MDBList first because they are already raw-synced; Trakt next because access/refresh tokens and device codes are plaintext; Simkl last into the shared broker format. On successful migration, overwrite/remove legacy key and all `.bak` remnants (`DebridSettingsDataStore.kt:42-50`; `TraktAuthDataStore.kt:57-89`; `ProfileDataStoreFactory.kt:184-219`).
5. **Use provider device OAuth wherever possible.** Preserve the current QR/code/poll interaction but add provider/profile/scope/cost/data confirmation and safe logging (`DebridSettingsScreen.kt:1194-1283,1317-1415`).
6. **For raw keys, implement the HTTPS E2EE phone-link flow.** Do not reuse the general NanoHTTPD state server. Keep offline LAN import behind a separate, one-purpose, authenticated ciphertext endpoint only if product requirements justify its complexity.
7. **Make grants independent of profile source sharing.** Profile is the default scope; device scope and every plugin/addon consumer require adult opt-in. Never infer credential access from `usesPrimaryAddons` or `usesPrimaryPlugins` (`AddonPreferences.kt:38-44`; `PluginDataStore.kt:44-50`).
8. **Deny external DEX BYOK until isolation exists.** QuickJS can use operation-level grants after settings/key injection and fetch/log paths are hardened. DEX must move to an isolated service with broker-mediated networking before it can receive any credential capability (`PluginRuntime.kt:448-452,506-558`; `DataStore.kt:13-33`).
9. **Create one redaction library and enforce it at compile time and at Sentry egress.** Replace no-op `urlForLog`/`rawForLog`, remove QR raw logging and plugin body previews, scrub exception/breadcrumb fields, then make user-facing Sentry claims match tested behavior (`LogDiagnostics.kt:3-25`; `SentryInitializer.kt:73-76`; `strings.xml:541-544`).
10. **Prepare target SDK 37 now.** Add an explicit LAN-config permission state machine and Android 17 tests, but request broad LAN access only when the user starts a feature that actually accepts LAN traffic ([Android 17 target behavior, updated 2026-08-28](https://developer.android.com/about/versions/17/behavior-changes-17#local-network-protection-permission)).

## Open Questions

1. **Backend credential protections are not in the inspected repository.** The SQL/functions for `sync_push_provider_credentials`, RLS policy, at-rest encryption, service-role access, retention, audit, and deletion could not be verified. Backend migrations and deployment configuration are required to determine current operator/compromise exposure (`ProviderCredentialSyncService.kt:150-188`).
2. **Supabase Auth's persisted session format is unresolved here.** Nuvio enables SDK auto-load/auto-save, but this repository does not define the SDK storage adapter. Dependency source/version and on-device file inspection are needed to determine whether the Nuvio account refresh token is plaintext (`SupabaseModule.kt:100-104`).
3. **Cross-device E2EE recovery policy is a product/security decision.** A Keystore key cannot simply migrate across devices. Resolve whether users re-enter keys per TV (recommended v1), approve new devices from an existing device, use account-recovery encryption, or accept server custody.
4. **Provider-specific revoke/test/cost APIs need a provider matrix.** Exact endpoints, minimum scopes, identity/quota response fields, key prefixes, pricing units, model/language availability, and true revocation, not merely local disconnect,must be validated against each chosen AI provider before implementation.
5. **External DEX compatibility may conflict with isolation.** Determine which extensions require activity context, arbitrary app-private storage, native libraries, unrestricted network, or host class reflection before committing to an `isolatedProcess` runner. Without isolation, the security answer remains no DEX BYOK.
6. **Addon URL migration needs compatibility policy.** Some installed addon URLs may already contain functional tokens in path/query. Decide whether to warn and quarantine, redact only telemetry/LAN display, or migrate supported addons to broker handles without breaking user installations (`AddonPreferences.kt:53-75`; `AddonSyncService.kt:59-69`).
7. **Android 17 behavior is current official guidance as of 2026-08-29, but Nuvio still targets 36.** Reconfirm permission names, TV-device availability, system-picker support, and final platform behavior immediately before raising `targetSdk` to 37 ([Android 17 target behavior, updated 2026-08-28](https://developer.android.com/about/versions/17/behavior-changes-17)).
